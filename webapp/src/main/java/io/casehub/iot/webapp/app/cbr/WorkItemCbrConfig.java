package io.casehub.iot.webapp.app.cbr;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "casehub.iot.webapp.cbr.work-item")
public interface WorkItemCbrConfig {

    @WithDefault("true")
    boolean enabled();

    @WithDefault("20")
    int topK();

    @WithDefault("0.3")
    double minSimilarity();
}
