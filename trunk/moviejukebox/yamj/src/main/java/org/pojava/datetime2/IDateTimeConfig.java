package org.pojava.datetime2;

/*
 Copyright 2008-09 John Pile

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * This interface defines methods essential for global configuration of the DateTime object.
 *
 * @author John Pile
 *
 */
public interface IDateTimeConfig {

    /**
     * British DD/MM/YYYY vs. Western MM/DD/YYYY
     *
     * @return True if parser interprets DD/MM/YYYY vs MM/DD/YYYY.
     */
    boolean isDmyOrder();

    /**
     * Map of timezones and their offsets.
     *
     * You can customize the default map to add your own timezones by referencing other timezones.
     *
     * @return TimeZones.
     */
    Map<String, String> getTzMap();

    /**
     * Language support for interpreting names of months.
     *
     * @return Array of supported language refs.
     */
    Object[] getSupportedLanguages();

    String[] getMonthArray(String langAbbr);

    TimeZone getInputTimeZone();

    TimeZone getOutputTimeZone();

    Locale getLocale();

    String getFormat();

    String getBcPrefix();

    int getEpochDOW();

    TimeZone lookupTimeZone(String str);

    boolean isUnspecifiedCenturyAlwaysInPast();

}
