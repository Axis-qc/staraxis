package staraxis.webnet;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ModOrder {

    public int schemaVersion = 1;
    public List<String> order = new ArrayList<>();
}
