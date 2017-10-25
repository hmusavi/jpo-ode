package us.dot.its.jpo.ode.plugin.j2735.builders.timstorage;

import com.fasterxml.jackson.annotation.JsonProperty;

import us.dot.its.jpo.ode.model.OdeObject;

public class Anchor extends OdeObject {
   private static final long serialVersionUID = 1L;

   private String elevation;

   @JsonProperty("long")
   private String lon; // TODO needs to be "long"

   private String lat;

   public String getElevation() {
      return elevation;
   }

   public void setElevation(String elevation) {
      this.elevation = elevation;
   }

   public String getlon() {
      return lon;
   }

   public void setlon(String lon) {
      this.lon = lon;
   }

   public String getLat() {
      return lat;
   }

   public void setLat(String lat) {
      this.lat = lat;
   }

}