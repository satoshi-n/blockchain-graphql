import { Client, types } from "cassandra-driver";
import { LimitedCapacityClient } from "./limited-capacity-client";
import { Mempool } from "./mempool";
import { Coin } from "./models/coin";
import { RpcClient } from "./rpc-client";

function arraysEqual(a, b) {
    if (a === b) return true;
    if (a == null || b == null) return false;
    if (a.length !== b.length) return false;

    // If you don't care about the order of the elements inside
    // the array, you should sort both arrays here.
    // Please note that calling sort on an array will modify that array.
    // you might want to clone your array first.

    for (var i = 0; i < a.length; ++i) {
        if (a[i] !== b[i]) return false;
    }
    return true;
}

export class CoinsUpdater {
    constructor(private nameToCoin: Map<string, Coin>, private client: LimitedCapacityClient, private coins_keyspace: string) {
    }

    private timeout: NodeJS.Timeout;

    private async updateRow(row: types.Row): Promise<void> {
        let name = row.get("name");
        let coin: Coin = this.nameToCoin.get(name);
        if (coin === undefined) coin = new Coin();
        //let coin: Coin = new Coin();
        coin.name = row.get("name");
        coin.keyspace = row.get("key_space");
        this.nameToCoin.set(coin.name, coin);
        let rpc_urls: string[] = row.get("rpc_urls");
        if (!arraysEqual(coin.rpc_urls, rpc_urls)) {
            coin.rpc_urls = rpc_urls;
            if (rpc_urls && rpc_urls.length > 0) {
                let rpcClient = new RpcClient(rpc_urls, process.env.BLOCKCHAIN_RPC_USERNAME, process.env.BLOCKCHAIN_RPC_PASSWORD);
                let mempool = new Mempool(rpcClient, this.client, coin);
                await mempool.start();
                coin.mempool = mempool;
            } else {
                if (coin.mempool) {
                    coin.mempool.stop();
                    delete coin.mempool;
                }
            }
        }
    }

    private async update(): Promise<void> {
        let coins: types.ResultSet = await this.client.execute("SELECT * FROM "+this.coins_keyspace+".available_coins");
        let promises: Promise<void>[] = [];
        coins.rows.forEach(row => {
            promises.push(this.updateRow(row));
        });
        await Promise.all(promises);
    }

    private async updater() {
        try {
            await this.update();
        } catch(err) {
        }
        this.timeout = setTimeout(() => {
            this.updater();
        }, 1000);
    }

    public async start(): Promise<void> {
        await this.updater();
    }

    public stop(): void {
        clearTimeout(this.timeout);
    }
}