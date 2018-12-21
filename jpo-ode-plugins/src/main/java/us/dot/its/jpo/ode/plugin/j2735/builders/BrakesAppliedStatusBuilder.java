/*******************************************************************************
 * Copyright 2018 572682
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy
 * of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/
package us.dot.its.jpo.ode.plugin.j2735.builders;

import com.fasterxml.jackson.databind.JsonNode;

import us.dot.its.jpo.ode.plugin.j2735.J2735BrakeAppliedStatus;

public class BrakesAppliedStatusBuilder {

   public enum BrakeAppliedStatusNames {
         unavailable, leftFront, leftRear, rightFront, rightRear
   }
   
   private BrakesAppliedStatusBuilder() {
   }

   public static J2735BrakeAppliedStatus genericBrakeAppliedStatus(JsonNode wheelBrakes) {

      J2735BrakeAppliedStatus returnValue = new J2735BrakeAppliedStatus();

      // wheelbrakes is a backwards bitstring
      char[] wb = wheelBrakes.asText().trim().toCharArray();

      for (char i = 0; i < wb.length; i++) {
         String eventName = BrakeAppliedStatusNames.values()[i].name();
         Boolean eventStatus = (wb[i] == '1' ? true : false);
         returnValue.put(eventName, eventStatus);
      }

      return returnValue;
   }

}
