package com.creadeep.kazanio;

public class TextUtils {

  public static String extractDigits (String inputString){
    if(inputString != null)
      return inputString.replaceAll("[^0-9]", ""); // leave only digits
    else
      return null;
  }

  public static String removeDigits (String inputString){
    if(inputString != null)
      return inputString.replaceAll("\\d",""); // leave only letters
    else
      return null;
  }

  /**
   * Removes space and newpage characters
   * @param inputString input string
   * @return string without spaces
   */
  public static String removeSpaces (String inputString){
    if(inputString != null)
      return inputString.replaceAll(" ", "").replaceAll("\n", ""); // remove space & newline
    else
      return null;
  }
}
