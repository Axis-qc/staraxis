package staraxis.ui.json;

import java.util.List;
import java.util.Map;

public interface DataProvider {
    List<Map<String, Object>> getData(String source);
}
