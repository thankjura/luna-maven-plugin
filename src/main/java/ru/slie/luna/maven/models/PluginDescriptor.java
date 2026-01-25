package ru.slie.luna.maven.models;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class PluginDescriptor {
    @JsonProperty(required = true)
    private String key;
    @JsonProperty(required = true)
    private String name;
    private String description;
    @JsonProperty(required = true)
    private String version;
    @JsonProperty(required = true)
    private Vendor vendor;
    private List<String> scanPackages;
    private List<Resource> resources;
    private List<WebRoute> routes;
    private List<Component> components;

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getVersion() {
        return version;
    }

    public List<Component> getComponents() {
        return components;
    }

    public Vendor getVendor() {
        return vendor;
    }

    public List<String> getScanPackages() {
        return scanPackages;
    }

    public List<Resource> getResources() {
        return resources;
    }

    public List<WebRoute> getRoutes() {
        return routes;
    }

    public static class Component {
        @JsonProperty(required = true)
        private String key;
        @JsonProperty(required = true)
        private String name;
        @JsonProperty(required = true)
        private String className;
        private List<Resource> resources;


        public String getName() {
            return name;
        }

        public String getClassName() {
            return className;
        }

        public String getKey() {
            return key;
        }

        public List<Resource> getResources() {
            return resources;
        }
    }

    public static class Vendor {
        @JsonProperty(required = true)
        private String name;
        @JsonProperty(required = true)
        private String url;

        public String getName() {
            return name;
        }

        public String getUrl() {
            return url;
        }
    }

    public static class Resource {
        @JsonProperty(required = true)
        private String path;
        @JsonProperty(required = true)
        private String key;
        @JsonProperty(required = true)
        private PluginResourceType type;

        public String getPath() {
            return path;
        }

        public PluginResourceType getType() {
            return type;
        }

        public String getKey() {
            return key;
        }
    }

    public static class WebRoute {
        @JsonProperty(required = true)
        private String routeName;
        @JsonProperty(required = true)
        private String routePath;
        @JsonProperty(required = true)
        private String component;
        private List<WebRoute> children;
        private String parent;

        public String getRouteName() {
            return routeName;
        }

        public String getRoutePath() {
            return routePath;
        }

        public String getComponent() {
            return component;
        }

        public List<WebRoute> getChildren() {
            return children;
        }

        public String getParent() {
            return parent;
        }
    }
}
