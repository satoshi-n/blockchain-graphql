package io.github.anttikaikkonen.blockchainanalyticsflink.statefun.cassandraexecutor;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SetDailyBalanceChange {
    private int epochDate;
    private long balanceChange;
}
