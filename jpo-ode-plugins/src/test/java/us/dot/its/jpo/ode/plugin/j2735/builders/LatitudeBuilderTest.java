package us.dot.its.jpo.ode.plugin.j2735.builders;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class LatitudeBuilderTest {

   @Test
   public void testConversion() throws JsonProcessingException, IOException {
      ObjectMapper mapper = new ObjectMapper();
      JsonNode testInput = mapper.readTree("451234567");
      BigDecimal expectedValue = BigDecimal.valueOf(45.1234567);

      assertEquals(expectedValue, LatitudeBuilder.genericLatitude(testInput));
   }
   
   @Test
   public void testConversionDeticatedNullValue() throws JsonProcessingException, IOException {
      ObjectMapper mapper = new ObjectMapper();
      JsonNode testInput = mapper.readTree("900000001");
      BigDecimal expectedValue = null;

      assertEquals(expectedValue, LatitudeBuilder.genericLatitude(testInput));
   }
   
   @Test
   public void testConstructorIsPrivate()
         throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
      Constructor<LatitudeBuilder> constructor = LatitudeBuilder.class.getDeclaredConstructor();
      assertTrue(Modifier.isPrivate(constructor.getModifiers()));
      constructor.setAccessible(true);
      try {
         constructor.newInstance();
         fail("Expected IllegalAccessException.class");
      } catch (Exception e) {
         assertEquals(InvocationTargetException.class, e.getClass());
      }
   }
   
   
}
