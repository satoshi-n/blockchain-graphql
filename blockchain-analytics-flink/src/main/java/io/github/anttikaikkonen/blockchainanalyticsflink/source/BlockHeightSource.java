package io.github.anttikaikkonen.blockchainanalyticsflink.source;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import io.github.anttikaikkonen.blockchainanalyticsflink.casssandra.CassandraSessionBuilder;
import io.github.anttikaikkonen.bitcoinrpcclientjava.RpcClient;
import java.util.function.Supplier;
import lombok.Builder;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.metrics.Gauge;
import org.apache.flink.runtime.state.FunctionInitializationContext;
import org.apache.flink.runtime.state.FunctionSnapshotContext;
import org.apache.flink.streaming.api.checkpoint.CheckpointedFunction;
import org.apache.flink.streaming.api.functions.source.RichSourceFunction;

public class BlockHeightSource extends RichSourceFunction<Integer> implements CheckpointedFunction {
    
    private volatile boolean isRunning = true;
    
    private transient ListState<Integer> checkpointedHeight;
    private int height = 0;
    
    private final int minConfirmations;
    private final long pollingInterval;
    private final int concurrentBlocks;
    
    private final Supplier<RpcClient> rpcClientSupplier;
    private final CassandraSessionBuilder sessionBuilder;
    private transient RpcClient rpcClient;
    private transient Session session;
    private transient PreparedStatement heightStatement;
    
    @Builder()
    public BlockHeightSource(Integer minConfirmations, Long pollingInterval, Supplier<RpcClient> rpcClientSupplier, CassandraSessionBuilder sessionBuilder, Integer concurrentBlocks) {
        this.minConfirmations = minConfirmations == null ? 5 : minConfirmations;
        this.pollingInterval = pollingInterval == null ? 1000l : pollingInterval;
        this.rpcClientSupplier = rpcClientSupplier;
        this.sessionBuilder = sessionBuilder;
        this.concurrentBlocks = concurrentBlocks == null ? 200 : concurrentBlocks;
    }
    
    @Override
    public void run(SourceContext<Integer> ctx) throws Exception {
        System.out.println("Starting block height source from height "+this.height);
        while (this.isRunning) {
            long blockCount = this.rpcClient.getBlockCount().toCompletableFuture().get();
            long targetHeight = blockCount-this.minConfirmations;
            if (this.height <= targetHeight) {
                while (this.height <= targetHeight && this.isRunning) {
                    if (this.height%5 == 0 && this.height-this.concurrentBlocks >= 0) {
                        if (this.session == null) {
                            Thread.sleep(1);
                        } else {
                            ResultSet res = this.session.execute(this.heightStatement.bind(this.height-this.concurrentBlocks));
                            if (res.one() == null) {
                                Thread.sleep(10);
                                continue;
                            }
                        }
                    }
                    synchronized (ctx.getCheckpointLock()) {
                        ctx.collect(this.height);
                        this.height++;
                    }
                }
            } else {
                Thread.sleep(this.pollingInterval);
            }
        }
    }

    @Override
    public void cancel() {
        this.isRunning = false;
    }

    @Override
    public void snapshotState(FunctionSnapshotContext context) throws Exception {
        this.checkpointedHeight.clear();
        this.checkpointedHeight.add(height);
    }

    @Override
    public void initializeState(FunctionInitializationContext context) throws Exception {
        this.checkpointedHeight = context.getOperatorStateStore().getListState(new ListStateDescriptor<>("height", Integer.class));
        if (context.isRestored()) {
            for (Integer height : this.checkpointedHeight.get()) {
                this.height = height;
            }
        }
    }

    @Override
    public void close() throws Exception {
        System.out.println("HeightSource close");
        if (this.session != null) {
            System.out.println("closing cluster");
            this.session.getCluster().close();
        }
        this.session = null;
        this.rpcClient.close();
        this.rpcClient = null;
        this.heightStatement = null;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        System.out.println("HeightSource open");
        this.rpcClient = rpcClientSupplier.get();
        if (sessionBuilder != null) {
            this.session = sessionBuilder.build();
            this.heightStatement = this.session.prepare("SELECT tx_n FROM confirmed_transaction WHERE height = :height AND tx_n = 0");
        } else {
            this.session = null;
            this.heightStatement = null;
        }
        getRuntimeContext().getMetricGroup().gauge("Current height", new Gauge<Integer>() {
            @Override
            public Integer getValue() {
                return height;
            }
        });
    }

}
