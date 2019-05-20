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
package us.dot.its.jpo.ode.services.asn1;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.StampedLock;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import us.dot.its.jpo.ode.OdeProperties;
import us.dot.its.jpo.ode.context.AppContext;
import us.dot.its.jpo.ode.eventlog.EventLogger;
import us.dot.its.jpo.ode.model.OdeAsn1Data;
import us.dot.its.jpo.ode.plugin.ServiceRequest;
import us.dot.its.jpo.ode.services.asn1.Asn1CommandManager.Asn1CommandManagerException;
import us.dot.its.jpo.ode.traveler.TimTransmogrifier;
import us.dot.its.jpo.ode.util.CodecUtils;
import us.dot.its.jpo.ode.util.JsonUtils;
import us.dot.its.jpo.ode.util.JsonUtils.JsonUtilsException;
import us.dot.its.jpo.ode.util.XmlUtils;
import us.dot.its.jpo.ode.wrapper.AbstractSubscriberProcessor;
import us.dot.its.jpo.ode.wrapper.MessageConsumer;
import us.dot.its.jpo.ode.wrapper.MessageProducer;

public class Asn1EncodedDataRouter extends AbstractSubscriberProcessor<String, String> {

   private static final String BYTES = "bytes";

  private static final String MESSAGE_FRAME = "MessageFrame";

  private static final String ERROR_ON_DDS_DEPOSIT = "Error on DDS deposit.";

  public static class Asn1EncodedDataRouterException extends Exception {

      private static final long serialVersionUID = 1L;

      public Asn1EncodedDataRouterException(String string) {
         super(string);
      }

   }

   private Logger logger = LoggerFactory.getLogger(this.getClass());

   private OdeProperties odeProperties;
   private MessageProducer<String, String> stringMsgProducer;
   private Asn1CommandManager asn1CommandManager;

   private String requestId;
   private StampedLock lock;
   private long stamp;
   private Asn1EncodedDataConsumer<String, String> consumer;

   public Asn1EncodedDataRouter(OdeProperties odeProperties, String requestId) {
      super();

      this.odeProperties = odeProperties;

      this.stringMsgProducer = MessageProducer.defaultStringMessageProducer(odeProperties.getKafkaBrokers(),
            odeProperties.getKafkaProducerType(), this.odeProperties.getKafkaTopicsDisabledSet());

      this.asn1CommandManager = new Asn1CommandManager(odeProperties);

      this.requestId = requestId;
      this.lock = new StampedLock();

      this.stamp = this.lock.writeLock();

      consumer = new Asn1EncodedDataConsumer<String, String>(
            odeProperties.getKafkaBrokers(), this.getClass().getSimpleName() + requestId, this,
            MessageConsumer.SERIALIZATION_STRING_DESERIALIZER);
   }

   @Override
   public HashMap<String, String> process(String consumedData) {
     
     unlock();

     HashMap<String, String> responseList = null;

     try {
         logger.debug("Consumed: {}", consumedData);
         JSONObject consumedObj = XmlUtils.toJSONObject(consumedData).getJSONObject(OdeAsn1Data.class.getSimpleName());

         /*
          * When receiving the 'rsus' in xml, since there is only one 'rsu' and
          * there is no construct for array in xml, the rsus does not translate
          * to an array of 1 element. The following workaround, resolves this
          * issue.
          */
         JSONObject metadata = consumedObj.getJSONObject(AppContext.METADATA_STRING);

         if (metadata.has(TimTransmogrifier.REQUEST_STRING)) {
            JSONObject request = metadata.getJSONObject(TimTransmogrifier.REQUEST_STRING);
            JSONObject ode = request.getJSONObject("ode");

            if (ode.has("requestId")) {
              if (ode.getString("requestId").equals(requestId)) {
                if (request.has(TimTransmogrifier.RSUS_STRING)) {
                  JSONObject rsusIn = (JSONObject) request.get(TimTransmogrifier.RSUS_STRING);
                  if (rsusIn.has(TimTransmogrifier.RSUS_STRING)) {
                    Object rsu = rsusIn.get(TimTransmogrifier.RSUS_STRING);
                    JSONArray rsusOut = new JSONArray();
                    if (rsu instanceof JSONArray) {
                      logger.debug("Multiple RSUs exist in the request: {}", request);
                      JSONArray rsusInArray = (JSONArray) rsu;
                      for (int i = 0; i < rsusInArray.length(); i++) {
                        rsusOut.put(rsusInArray.get(i));
                      }
                      request.put(TimTransmogrifier.RSUS_STRING, rsusOut);
                    } else if (rsu instanceof JSONObject) {
                      logger.debug("Single RSU exists in the request: {}", request);
                      rsusOut.put(rsu);
                      request.put(TimTransmogrifier.RSUS_STRING, rsusOut);
                    } else {
                      logger.debug("No RSUs exist in the request: {}", request);
                      request.remove(TimTransmogrifier.RSUS_STRING);
                    }
                  }
               }

               // Convert JSON to POJO
               ServiceRequest servicerequest = getServicerequest(consumedObj);

               processEncodedTim(servicerequest, consumedObj);
              }
            }
            
         } else {
            throw new Asn1EncodedDataRouterException("Invalid or missing '"
                + TimTransmogrifier.REQUEST_STRING + "' object in the encoder response");
         }
      } catch (Exception e) {
         String msg = "Error in processing received message from ASN.1 Encoder module: " + consumedData;
         EventLogger.logger.error(msg, e);
         logger.error(msg, e);
      }
      return responseList;
   }

   public ServiceRequest getServicerequest(JSONObject consumedObj) {
      String sr = consumedObj.getJSONObject(AppContext.METADATA_STRING).getJSONObject(TimTransmogrifier.REQUEST_STRING).toString();
      logger.debug("ServiceRequest: {}", sr);

      // Convert JSON to POJO
      ServiceRequest serviceRequest = null;
      try {
         serviceRequest = (ServiceRequest) JsonUtils.fromJson(sr, ServiceRequest.class);

      } catch (Exception e) {
         String errMsg = "Malformed JSON.";
         EventLogger.logger.error(errMsg, e);
         logger.error(errMsg, e);
      }

      return serviceRequest;
   }

   public Map<String, String>  processEncodedTim(ServiceRequest request, JSONObject consumedObj) {

     // Send TIMs and record results
     Map<String, String> responseList = null;

     JSONObject dataObj = consumedObj.getJSONObject(AppContext.PAYLOAD_STRING).getJSONObject(AppContext.DATA_STRING);

      // CASE 1: no SDW in metadata (SNMP deposit only)
      // - sign MF
      // - send to RSU
      // CASE 2: SDW in metadata but no ASD in body (send back for another
      // encoding)
      // - sign MF
      // - send to RSU
      // - craft ASD object
      // - publish back to encoder stream
      // CASE 3: If SDW in metadata and ASD in body (double encoding complete)
      // - send to DDS

      if (!dataObj.has(Asn1CommandManager.ADVISORY_SITUATION_DATA_STRING)) {
         logger.debug("Unsigned message received");
         // We don't have ASD, therefore it must be just a MessageFrame that needs to be signed
         // No support for unsecured MessageFrame only payload.
         // Cases 1 & 2
         // Sign and send to RSUs

         JSONObject mfObj = dataObj.getJSONObject(MESSAGE_FRAME);

         String hexEncodedTim = mfObj.getString(BYTES);
         logger.debug("Encoded message - phase 1: {}", hexEncodedTim);

         if (odeProperties.dataSigningEnabled()) {
            logger.debug("Sending message for signature!");
            String base64EncodedTim = CodecUtils.toBase64(
               CodecUtils.fromHex(hexEncodedTim));
            String signedResponse = asn1CommandManager.sendForSignature(base64EncodedTim );

            try {
               hexEncodedTim = CodecUtils.toHex(
                  CodecUtils.fromBase64(
                     JsonUtils.toJSONObject(signedResponse).getString("result")));
            } catch (JsonUtilsException e1) {
               logger.error("Unable to parse signed message response {}", e1);
            }
         }

         if (null != request.getSnmp() && null != request.getRsus() && null != hexEncodedTim) {
            logger.info("Sending message to RSUs...");
            responseList = asn1CommandManager.sendToRsus(request, hexEncodedTim);
            logger.info("TIM deposit response {}", responseList);
            
         }

         if (request.getSdw() != null) {
            // Case 2 only

            logger.debug("Publishing message for round 2 encoding!");
            String xmlizedMessage = asn1CommandManager.packageSignedTimIntoAsd(request, hexEncodedTim);

            stringMsgProducer.send(odeProperties.getKafkaTopicAsn1EncoderInput(), null, xmlizedMessage);
         }

      } else {
         //We have encoded ASD. It could be either UNSECURED or secured.
         logger.debug("securitySvcsSignatureUri = {}", odeProperties.getSecuritySvcsSignatureUri());

         if (odeProperties.dataSigningEnabled()) {
            logger.debug("Signed message received. Depositing it to SDW.");
            // We have a ASD with signed MessageFrame
            // Case 3
            JSONObject asdObj = dataObj.getJSONObject(Asn1CommandManager.ADVISORY_SITUATION_DATA_STRING);
            String sdwMessage;
            try {
              asn1CommandManager.depositToSdw(asdObj.getString(BYTES));
              sdwMessage = "\"sdw_deposit\":{\"success\":\"true\"}";
              logger.info("DDS deposit successful.");
            } catch (JSONException | Asn1CommandManagerException e) {
              sdwMessage = "\"sdw_deposit\":{\"success\":\"false\"}";
            	String msg = ERROR_ON_DDS_DEPOSIT;
              logger.error(msg, e);
            }
            
            if (responseList == null)
            	responseList = new HashMap<>();
            
            responseList.put("sdwMessage", sdwMessage);
         } else {
            logger.debug("Unsigned ASD received. Depositing it to SDW.");
            //We have ASD with UNSECURED MessageFrame
            processEncodedTimUnsecured(request, consumedObj);
         }
      }

      String msg = "TIM deposit response " + responseList;
      logger.info(msg);
      EventLogger.logger.info(msg);

      return responseList;
   }

   public void processEncodedTimUnsecured(ServiceRequest request, JSONObject consumedObj) {
      // Send TIMs and record results
      HashMap<String, String> responseList = new HashMap<>();

      JSONObject dataObj = consumedObj
            .getJSONObject(AppContext.PAYLOAD_STRING)
            .getJSONObject(AppContext.DATA_STRING);

      if (null != request.getSdw()) {
         JSONObject asdObj = null;
         if (dataObj.has(Asn1CommandManager.ADVISORY_SITUATION_DATA_STRING)) {
            asdObj = dataObj.getJSONObject(Asn1CommandManager.ADVISORY_SITUATION_DATA_STRING);
         } else {
            logger.error("ASD structure present in metadata but not in JSONObject!");
         }

        if (null != asdObj) {
           String asdBytes = asdObj.getString(BYTES);

           // Deposit to DDS
           String ddsMessage = "";
           try {
              asn1CommandManager.depositToSdw(asdBytes);
              ddsMessage = "\"dds_deposit\":{\"success\":\"true\"}";
              logger.info("DDS deposit successful.");
           } catch (Exception e) {
              ddsMessage = "\"dds_deposit\":{\"success\":\"false\"}";
              String msg = ERROR_ON_DDS_DEPOSIT;
              logger.error(msg, e);
              EventLogger.logger.error(msg, e);
           }

           responseList.put("ddsMessage", ddsMessage);
        } else if (logger.isErrorEnabled()) { // Added to avoid Sonar's "Invoke method(s) only conditionally." code smell
          String msg = "ASN.1 Encoder did not return ASD encoding {}";
          EventLogger.logger.error(msg, consumedObj.toString());
          logger.error(msg, consumedObj.toString());
        }
      }

      if (dataObj.has(MESSAGE_FRAME)) {
         JSONObject mfObj = dataObj.getJSONObject(MESSAGE_FRAME);
         String encodedTim = mfObj.getString(BYTES);
         logger.debug("Encoded message - phase 2: {}", encodedTim);

        // only send message to rsu if snmp, rsus, and message frame fields are present
        if (null != request.getSnmp() && null != request.getRsus() && null != encodedTim) {
           logger.debug("Encoded message phase 3: {}", encodedTim);
           Map<String, String> rsuResponseList =
                 asn1CommandManager.sendToRsus(request, encodedTim);
           responseList.putAll(rsuResponseList);
         }
      }

      logger.info("TIM deposit response {}", responseList);
   }

   public Future<?> consume(ExecutorService executor, String... inputTopics) {
     logger.info("consuming from topic {}", Arrays.asList(inputTopics).toString());

     Future<?> future = executor.submit(new Callable<Object>() {
        @Override
        public Object call() {
           Object result = null;

           consumer.subscribe(inputTopics);
           
           // Now that the subscription is established, we can release the synchronization lock
           unlock();
           
           result = consumer.consume();
           consumer.close();
           return result;
        }
     });
     return future;
  }


   public void waitTillReady() {
     try {
        long stamp2 = lock.tryWriteLock(odeProperties.getRestResponseTimeout(), TimeUnit.MILLISECONDS);
        if (stamp2 != 0) {
           lock.unlockWrite(stamp2);
        }
     } catch (Exception e) {
        logger.error("ASN.1 Encoded Data Consumer took too long to start", e);
     } finally {
        unlock();
     }
  }

   private void unlock() {
		if (stamp != 0) {
			lock.unlockWrite(stamp);
			stamp = 0;
		}
	}

	public void close() {
		consumer.close();
	}
}
