package com.example.smartintersections.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class IntersectionResponse {

    @SerializedName("features")
    private List<Feature> features;

    public List<Feature> getFeatures() {
        return features;
    }

    public void setFeatures(List<Feature> features) {
        this.features = features;
    }

    public static class Feature {
        @SerializedName("geometry")
        private Geometry geometry;

        @SerializedName("properties")
        private Properties properties;

        public Geometry getGeometry() {
            return geometry;
        }

        public void setGeometry(Geometry geometry) {
            this.geometry = geometry;
        }

        public Properties getProperties() {
            return properties;
        }

        public void setProperties(Properties properties) {
            this.properties = properties;
        }
    }

    public static class Geometry {
        @SerializedName("coordinates")
        private List<Double> coordinates;

        public List<Double> getCoordinates() {
            return coordinates;
        }

        public void setCoordinates(List<Double> coordinates) {
            this.coordinates = coordinates;
        }
    }

    public static class Properties {
        @SerializedName("type")
        private String type;

        @SerializedName("description")
        private String description;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}
