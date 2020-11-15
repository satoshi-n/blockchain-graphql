package io.github.anttikaikkonen.blockchainanalyticsflink.statefun.unionfind;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
public class MergeOperation {
    String to;
    
    List<String> visited;
    
}
