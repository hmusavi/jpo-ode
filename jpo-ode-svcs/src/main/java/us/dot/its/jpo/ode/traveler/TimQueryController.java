package us.dot.its.jpo.ode.traveler;

import java.io.IOException;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.PDU;
import org.snmp4j.ScopedPDU;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.VariableBinding;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import us.dot.its.jpo.ode.OdeProperties;
import us.dot.its.jpo.ode.plugin.RoadSideUnit.RSU;
import us.dot.its.jpo.ode.snmp.SnmpSession;
import us.dot.its.jpo.ode.util.JsonUtils;

@Controller
public class TimQueryController {
   
   private static final Logger logger = LoggerFactory.getLogger(TimQueryController.class);
   
   private static final String ERRSTR = "error";

   private OdeProperties odeProperties;
   
   @Autowired
   public TimQueryController(OdeProperties odeProperties) {
      this.odeProperties = odeProperties;
   }

   /**
    * Checks given RSU for all TIMs set
    * 
    * @param jsonString
    *           Request body containing RSU info
    * @return list of occupied TIM slots on RSU
    */
   @ResponseBody
   @CrossOrigin
   @RequestMapping(value = "/tim/query", method = RequestMethod.POST)
   public synchronized ResponseEntity<String> bulkQuery(@RequestBody String jsonString) { // NOSONAR

      if (null == jsonString || jsonString.isEmpty()) {
         logger.error("Empty request.");
         return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(JsonUtils.jsonKeyValue(ERRSTR, "Empty request."));
      }

      RSU queryTarget = (RSU) JsonUtils.fromJson(jsonString, RSU.class);

      SnmpSession snmpSession = null;
      try {
         snmpSession = new SnmpSession(queryTarget);
         snmpSession.startListen();
      } catch (IOException e) {
         logger.error("Error creating SNMP session.", e);
         return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
               .body(JsonUtils.jsonKeyValue(ERRSTR, "Failed to create SNMP session."));
      }

      PDU pdu0 = new ScopedPDU();
      pdu0.setType(PDU.GET);
      PDU pdu1 = new ScopedPDU();
      pdu1.setType(PDU.GET);

      for (int i = 0; i < odeProperties.getRsuSrmSlots() - 50; i++) {
         pdu0.add(new VariableBinding(new OID("1.0.15628.4.1.4.1.11.".concat(Integer.toString(i)))));
      }

      for (int i = 50; i < odeProperties.getRsuSrmSlots(); i++) {
         pdu1.add(new VariableBinding(new OID("1.0.15628.4.1.4.1.11.".concat(Integer.toString(i)))));
      }

      ResponseEvent response0 = null;
      ResponseEvent response1 = null;
      try {
         response0 = snmpSession.getSnmp().send(pdu0, snmpSession.getTarget());
         response1 = snmpSession.getSnmp().send(pdu1, snmpSession.getTarget());
      } catch (IOException e) {
         logger.error("Error creating SNMP session.", e);
         return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
               .body(JsonUtils.jsonKeyValue(ERRSTR, "Failed to create SNMP session."));
      }

      // Process response
      if (response0 == null || response0.getResponse() == null || response1 == null
            || response1.getResponse() == null) {
         logger.error("RSU query failed, timeout.");
         return ResponseEntity.status(HttpStatus.BAD_REQUEST)
               .body(JsonUtils.jsonKeyValue(ERRSTR, "Timeout, no response from RSU."));
      }

      HashMap<String, Boolean> resultsMap = new HashMap<>();
      for (Object vbo : response0.getResponse().getVariableBindings().toArray()) {
         VariableBinding vb = (VariableBinding) vbo;
         if (vb.getVariable().toInt() == 1) {
            resultsMap.put(vb.getOid().toString().substring(21), true);
         }
      }

      for (Object vbo : response1.getResponse().getVariableBindings().toArray()) {
         VariableBinding vb = (VariableBinding) vbo;
         if (vb.getVariable().toInt() == 1) {
            resultsMap.put(vb.getOid().toString().substring(21), true);
         }
      }

      try {
         snmpSession.endSession();
      } catch (IOException e) {
         logger.error("Error closing SNMP session.", e);
      }

      logger.info("RSU query successful: {}", resultsMap.keySet());
      return ResponseEntity.status(HttpStatus.OK).body(JsonUtils.jsonKeyValue("indicies_set", resultsMap.keySet().toString()));
   }

}
