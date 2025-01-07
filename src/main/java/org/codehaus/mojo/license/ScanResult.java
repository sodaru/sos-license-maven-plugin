package org.codehaus.mojo.license;

import lombok.Data;

import java.util.List;

@Data
public class ScanResult {
    private String project;
    private String url;
    private Meta meta;
    private List<Dependency> dependencies;

    @Data
    static class Meta {
        private String submitterEmail;
        private String submitJobUrl;
    }
    @Data
    static
    class Dependency {
        private String name;
        private String version;
        private List<License> licenses;

        @Data
        static
        class License {
            private String id;
            private String uri;
        }
    }
}
