package io.github.anttikaikkonen.blockchainanalyticsflink.casssandra;

import com.datastax.driver.mapping.MappingManager;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.async.ResultFuture;
import org.apache.flink.streaming.api.functions.async.RichAsyncFunction;


public abstract class CassandraSaverFunction<E> extends RichAsyncFunction<E, Void>{

    private MappingManager manager;
    private CassandraSessionBuilder sessionBuilder;
    
    public CassandraSaverFunction(CassandraSessionBuilder sessionBuilder) {
        this.sessionBuilder = sessionBuilder;
    }
    
    @Override
    public void asyncInvoke(E input, ResultFuture<Void> resultFuture) throws Exception {
        ListenableFuture future = saveAsync(input);
        Futures.addCallback(future, new FutureCallback<Object>() {
            @Override
            public void onSuccess(Object arg0) {
                resultFuture.complete(new ArrayList<>());
            }

            @Override
            public void onFailure(Throwable ex) {
                resultFuture.completeExceptionally(ex);
            }
        });
        
    }
    
    public abstract ListenableFuture saveAsync(E input);

    @Override
    public void close() throws Exception {
        if (this.manager != null) {
            if (this.manager.getSession() != null) {
                this.manager.getSession().getCluster().close();
                this.manager = null;
            }
        }
    }
    
    
    @Override
    public void open(Configuration parameters) throws Exception {
        this.manager = new MappingManager(sessionBuilder.build());
        initMappers(manager);
    }
    
    public abstract void initMappers(MappingManager manager);

    
}