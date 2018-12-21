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
package us.dot.its.jpo.ode.udp.vsd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//import us.dot.its.jpo.ode.asn1.j2735.CVSampleMessageBuilder;

public class VsdReceiverFunctionalTest {

	private Logger logger = LoggerFactory.getLogger(this.getClass());
   //TODO open-ode
//	private static Coder coder = J2735.getPERUnalignedCoder();

//	@Test
//	@Ignore
//	public void test() throws SocketException {
//		int selfPort = 12321;
//		String targetHost = "localhost";
//		int targetPort = 5556;
//		DatagramSocket socket = new DatagramSocket(selfPort);
//		ServiceRequest sr = CVSampleMessageBuilder.buildVehicleSituationDataServiceRequest();
//		ByteArrayOutputStream sink = new ByteArrayOutputStream();
//		try {
//			coder.encode(sr, sink);
//			byte[] payload = sink.toByteArray();
//			logger.info("ODE: Sending VSD Deposit ServiceRequest ...");
//			socket.send(new DatagramPacket(payload, payload.length, new InetSocketAddress(targetHost, targetPort)));
//		} catch (EncodeFailedException | EncodeNotSupportedException | IOException e) {
//			logger.error("ODE: Error Sending VSD Deposit ServiceRequest", e);
//		}
//		if (socket != null)
//			socket.close();
//	}
}
