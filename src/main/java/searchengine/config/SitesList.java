package searchengine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "indexing-settings")
public class SitesList {
    private List<SiteConfig> siteConfigs;

    public SiteConfig findBySiteUrl(String url) {
        for (SiteConfig siteConfig : siteConfigs) {
            if(siteConfig.getUrl().equals(url)) {
                return siteConfig;
            }
        }

        return null;
    }
}
