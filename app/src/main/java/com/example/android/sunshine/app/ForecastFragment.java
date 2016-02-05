package com.example.android.sunshine.app;

/**
 * Created by rchamp on 12/27/2015.
 */

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * A fragment for showing the forecast data
 */
public class ForecastFragment extends Fragment {

    private final String LOG_TAG = ForecastFragment.class.getSimpleName();

    public ArrayAdapter<String> mForecastAdapter;
    public boolean mConvertToImperial = false;

    public ForecastFragment() {

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater menuInflater) {
        menuInflater.inflate(R.menu.forecast_fragment, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch (id) {

            case R.id.action_refresh:
                refreshWeatherData();
                return true;

            case R.id.action_settings:
                Intent settingsIntent = new Intent(getActivity(), SettingsActivity.class);
                startActivity(settingsIntent);
                return true;

            default:
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onStart(){
        super.onStart();
        refreshWeatherData();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        mForecastAdapter = new ArrayAdapter<String>(getActivity(),
                R.layout.list_item_forcast,
                R.id.list_item_forecast_textview,
                new ArrayList<String>());

        ListView forecastList = (ListView) rootView.findViewById(R.id.listview_forecast);
        forecastList.setAdapter(mForecastAdapter);

        forecastList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                Context context = getActivity().getApplicationContext();
                CharSequence weatherInfo = (CharSequence) adapterView.getItemAtPosition(i);
                if (weatherInfo != null) {
                    Intent detailIntent = new Intent(context, DetailActivity.class);
                    detailIntent.putExtra(Intent.EXTRA_TEXT, weatherInfo);
                    detailIntent.setType("text/plain");

                    startActivity(detailIntent);
                } else {
                    Log.w(LOG_TAG, "Weather info item not found.");
                }
            }
        });

        return rootView;
    }

    private void refreshWeatherData()
    {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());

        String locationKey = getString(R.string.pref_location_key);
        String defaultLocation = getString(R.string.pref_location_default);

        String unitsKey = getString(R.string.pref_units_key);
        String defaultUnits = getString(R.string.pref_units_default);

        mConvertToImperial = !prefs.getString(unitsKey, defaultUnits).equalsIgnoreCase(defaultUnits);

        new FetchWeatherTask().execute(prefs.getString(locationKey, defaultLocation));
    }

    public class FetchWeatherTask extends AsyncTask<String, Void, String[]> {

        private final String FORECAST_BASE_URL = "http://api.openweathermap.org/data/2.5/forecast/daily";
        private final String FORECAST_QUERY_PARAM = "q";
        private final String UNITS_PARAM = "units";
        private final String APPID_PARAM = "APPID";
        private final String COUNT_PARAM = "cnt";
        private final int NUM_DAYS = 7;

        @Override
        protected void onPostExecute(String[] weatherData) {
            super.onPostExecute(weatherData);

            mForecastAdapter.clear();

            for (String s : weatherData) {
                mForecastAdapter.add(s);
            }
        }

        @Override
        protected String[] doInBackground(String... postalCode) {
            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;

            String forecastJsonStr = null;
            try {
                if (postalCode.length == 0)
                    return null;

                URL url = new URL(BuildForecastUrl(postalCode[0], NUM_DAYS));

                // Create Request
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

                // Read input stream
                InputStream inputStream = urlConnection.getInputStream();
                StringBuffer buffer = new StringBuffer();
                if (inputStream == null) {
                    forecastJsonStr = null;
                }
                reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while ((line = reader.readLine()) != null) {
                    // Adding the newline here is helpful for debugging json
                    buffer.append(line + "\n");
                }

                if (buffer.length() == 0) {
                    forecastJsonStr = null;
                }
                forecastJsonStr = buffer.toString();
            }
            catch (IOException e) {
                Log.e(LOG_TAG, "Error ", e);
                forecastJsonStr = null;
            }
            finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }

                if (reader != null) {
                    try {
                        reader.close();
                    }
                    catch (final IOException e) {
                        Log.e(LOG_TAG, "Error closing stream", e);
                    }
                }
            }

            String[] weatherData;
            try {
                weatherData = getWeatherDataFromJson(forecastJsonStr, NUM_DAYS);
            }
            catch (JSONException e)
            {
                Log.e(LOG_TAG, "Error ", e);
                weatherData = null;
            }

            return weatherData;
        }

        private String BuildForecastUrl(String postalCode, int numDays)
        {
            // URL url = new URL("http://api.openweathermap.org/data/2.5/forecast/daily?q=" + postalCode + "&units=metric&cnt=7&APPID=" + BuildConfig.OPEN_WEATHER_MAP_API_KEY);
            Uri forecastUri = Uri.parse(FORECAST_BASE_URL).buildUpon()
                    .appendQueryParameter(FORECAST_QUERY_PARAM, postalCode)
                    .appendQueryParameter(UNITS_PARAM, "metric")
                    .appendQueryParameter(COUNT_PARAM, Integer.toString(numDays))
                    .appendQueryParameter(APPID_PARAM, BuildConfig.OPEN_WEATHER_MAP_API_KEY)
                    .build();

            return forecastUri.toString();
        }

        private String[] getWeatherDataFromJson(String forecastJsonStr, int numDays)
            throws JSONException {

            // The JSON Objects that we need from OWM api response
            final String OWM_LIST = "list";
            final String OWM_WEATHER = "weather";
            final String OWM_TEMPERATURE = "temp";
            final String OWM_MAX = "max";
            final String OWM_MIN = "min";
            final String OWM_DESCRIPTION = "main";

            String[] resultStrs = new String[numDays];

            if (forecastJsonStr == null)
            {
                Log.w(LOG_TAG, "JSON returned from OpenWeatherMap was empty");
                return resultStrs;
            }

            JSONObject forecastJson = new JSONObject(forecastJsonStr);
            JSONArray weatherArray = forecastJson.getJSONArray(OWM_LIST);

            // OWM returns time based on the local time of the city asked for. Because of this,
            // We need to know the GMT offset to translate properly

            // Dates are always sent in order from OWM, and the first day is always the current day...
            Time dayTime = new Time();
            dayTime.setToNow();

            // ... therefore we can get the GMT offset from that
            int julianStartDay = Time.getJulianDay(System.currentTimeMillis(), dayTime.gmtoff);

            dayTime = new Time();

            for (int i = 0; i < weatherArray.length(); i++) {
                // "Day, description, high/low"
                String day;
                String description;
                String highAndLow;

                JSONObject dayForecast = weatherArray.getJSONObject(i);

                long dateTime = dayTime.setJulianDay(julianStartDay + i);
                day = getReadableDateString(dateTime);

                // Weather object is a 1-element child array
                JSONObject weatherObject = dayForecast.getJSONArray(OWM_WEATHER).getJSONObject(0);
                description = weatherObject.getString(OWM_DESCRIPTION);

                JSONObject temperatureObject = dayForecast.getJSONObject(OWM_TEMPERATURE);
                double high = temperatureObject.getDouble(OWM_MAX);
                double low = temperatureObject.getDouble(OWM_MIN);

                if (mConvertToImperial) {
                    high = convertFromMetricToImperial(high);
                    low = convertFromMetricToImperial(low);
                }

                highAndLow = formatHighLows(high, low);

                resultStrs[i] = day + " - " + description + " - " + highAndLow;
            }

            return resultStrs;
        }

        private String getReadableDateString(long time) {
            // The OWM Api returns a Unix timestamp in seconds. This must be converted
            // to milliseconds in order to be converted into a valid date.

            // EEE = Day name in week
            // MMM = Month in year
            // dd = Day in month
            SimpleDateFormat shortenedDateFormat = new SimpleDateFormat("EEE MMM dd");
            return shortenedDateFormat.format(time);
        }

        // Metric (Celcius) to Imperial (Fahrenheit) formula:
        // F = (C * 1.8) + 32
        private double convertFromMetricToImperial(double metricTemp)
        {
            return (metricTemp * 1.8) + 32;
        }

        // To remove decimals from degrees for users
        private String formatHighLows(double high, double low) {
            long roundedHigh = Math.round(high);
            long roundedLow = Math.round(low);

            String highLowStr = roundedHigh + "/" + roundedLow;
            return highLowStr;
        }
    }
}
