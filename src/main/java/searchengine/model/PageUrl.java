package searchengine.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PageUrl {

    private List<PageUrl> children = new ArrayList<>();
    private PageUrl parent;
    private String headUrl;
    private String absolutePath;
    private String path;
    private String content;
    private int status;

    public PageUrl(String absolutePath) {
        this.absolutePath = absolutePath;
    }
}
