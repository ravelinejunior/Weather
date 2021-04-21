package br.com.weather.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import br.com.weather.R
import br.com.weather.api.WeatherService
import br.com.weather.model.WeatherModel
import br.com.weather.util.Constants
import br.com.weather.util.Constants.API_KEY
import br.com.weather.util.Constants.BASE_URL
import br.com.weather.util.Constants.METRICS_UNIT
import br.com.weather.util.Constants.PREFERENCES_KEY_NAME
import br.com.weather.util.Constants.WEATHER_RESPONSE_DATA
import com.google.android.gms.location.*
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.android.synthetic.main.activity_main.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var mFusedLocationClient: FusedLocationProviderClient

    private var mProgressDialog: Dialog? = null

    private lateinit var mSharedPref: SharedPreferences

    private lateinit var retrofit: Retrofit

    private var mLatitude: Double? = 0.0
    private var mLongitude: Double? = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)

        mSharedPref = getSharedPreferences(PREFERENCES_KEY_NAME, Context.MODE_PRIVATE)

        displayDataFromApi()

        setSupportActionBar(toolbar_main_id)
        toolbar_main_id.setTitleTextColor(resources.getColor(R.color.white))

        if (isLocationEnabled()) {
            Toast.makeText(applicationContext, "Your GPS is on!", Toast.LENGTH_SHORT).show()
            Dexter.withContext(this).withPermissions(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.INTERNET
            ).withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    if (report!!.areAllPermissionsGranted()) {

                        requestUserLocationData()
                    }

                    if (report.isAnyPermissionPermanentlyDenied) {
                        Toast.makeText(
                            this@MainActivity,
                            "You denied a location permission",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    request: MutableList<PermissionRequest>?,
                    token: PermissionToken?
                ) {
                    showPermissionsRequestPanel()
                }

            }).onSameThread().check()
        } else {
            Toast.makeText(applicationContext, "Please, turn on your GPS", Toast.LENGTH_SHORT)
                .show()
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }

        btn_searchCity_id.setOnClickListener(this)
    }


    private fun showPermissionsRequestPanel() {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle("Set permissions")
        builder.setMessage("You have to set the permissions to use the App")
        builder.setPositiveButton("Okay") { dialogInterface, _ ->
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                e.printStackTrace()
            }

        }

        builder.setNegativeButton("Cancel") { dialogInterface, _ ->
            dialogInterface.dismiss()
        }

        val alertDialog: AlertDialog = builder.create()
        alertDialog.show()

    }

    @SuppressLint("MissingPermission")
    private fun requestUserLocationData() {
        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest,
            mLocationCallback,
            Looper.myLooper()
        )
    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val mLastLocation: Location = result.lastLocation

            mLatitude = mLastLocation.latitude
            Log.i("mLastLocation", "Latitude: $mLatitude")

            mLongitude = mLastLocation.longitude
            Log.i("mLastLocation", "Longitude: $mLongitude")

            getLocationWeatherDetails()

        }
    }

    private fun getWeatherFromCityName(cityName: String) {
        if (Constants.isNetworkAvailable(applicationContext)) {
            if (cityName.isNotEmpty()) {
                retrofit = buildRetrofitInstance()

                val weatherService: WeatherService =
                    retrofit.create(WeatherService::class.java)


                val call: Call<WeatherModel> =
                    weatherService.getWeatherByCity(cityName, METRICS_UNIT, API_KEY)

                showDialogWait()

                call.enqueue(object : Callback<WeatherModel> {
                    override fun onFailure(call: Call<WeatherModel>, t: Throwable) {
                        hideProgressDialog()
                        Log.e("ResponseLocation", "onResponse: Error/ ${t.printStackTrace()}")
                    }

                    override fun onResponse(
                        call: Call<WeatherModel>,
                        response: Response<WeatherModel>
                    ) {
                        if (response.isSuccessful) {
                            if (response.body() != null) {
                                val weather: WeatherModel = response.body()!!
                                displayDataFromCityApi(weather)
                                hideProgressDialog()
                            }
                        } else {
                            hideProgressDialog()
                            val responseCode = response.code()
                            when (responseCode) {
                                400 -> {
                                    Log.e(
                                        "ResponseLocation",
                                        "onResponse: Bad Connection / $responseCode"
                                    )
                                }
                                404 -> {
                                    Log.e(
                                        "ResponseLocation",
                                        "onResponse: Not found / $responseCode"
                                    )
                                }
                                else -> Log.e(
                                    "ResponseLocation",
                                    "onResponse: Not founded $responseCode"
                                )
                            }
                        }
                    }

                })
            }
        } else {
            Toast.makeText(applicationContext, "No internet available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getLocationWeatherDetails() {
        if (Constants.isNetworkAvailable(applicationContext)) {
            retrofit = buildRetrofitInstance()
            val weatherService: WeatherService =
                retrofit.create<WeatherService>(WeatherService::class.java)

            val call: Call<WeatherModel> =
                weatherService.getWeather(mLatitude!!, mLongitude!!, METRICS_UNIT, API_KEY)

            showDialogWait()

            call.enqueue(object : Callback<WeatherModel> {
                override fun onFailure(call: Call<WeatherModel>, t: Throwable) {
                    hideProgressDialog()
                    Log.e("ResponseLocation", "onResponse: Error/ ${t.printStackTrace()}")
                }

                override fun onResponse(
                    call: Call<WeatherModel>,
                    response: Response<WeatherModel>?
                ) {
                    if (response!!.isSuccessful) {
                        if (response.body() != null) {
                            val weatherModel: WeatherModel = response.body()!!

                            //convert data to json
                            val weatherResponseJsonToString = Gson().toJson(weatherModel)
                            val editor = mSharedPref.edit()
                            editor.putString(WEATHER_RESPONSE_DATA, weatherResponseJsonToString)
                            editor.apply()

                            hideProgressDialog()
                            displayDataFromApi()
                        }
                    } else {
                        val responseCode = response.code()
                        when (responseCode) {
                            400 -> {
                                Log.e(
                                    "ResponseLocation",
                                    "onResponse: Bad Connection / $responseCode"
                                )
                            }
                            404 -> {
                                Log.e("ResponseLocation", "onResponse: Not found / $responseCode")
                            }
                            else -> Log.e(
                                "ResponseLocation",
                                "onResponse: Not founded $responseCode"
                            )
                        }
                    }
                }

            })

        } else {
            Toast.makeText(this, "Internet Off", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildRetrofitInstance(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL).addConverterFactory(GsonConverterFactory.create()).build()
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun showDialogWait() {
        mProgressDialog = Dialog(this)
        mProgressDialog?.setContentView(R.layout.dialog_custom_progress)
        mProgressDialog?.show()
    }

    private fun hideProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog?.dismiss()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun displayDataFromApi() {

        val weatherJsonResponse = mSharedPref.getString(WEATHER_RESPONSE_DATA, "")
        if (!weatherJsonResponse.isNullOrEmpty()) {
            val weather =
                Gson().fromJson<WeatherModel>(weatherJsonResponse, WeatherModel::class.java)


            for (i in weather.weather.indices) {
                if (weather != null) {
                    //  iv_main.setImageURI(Uri.parse(weather.weather[0].icon))
                    tv_main.text = (weather.weather[i].main)
                    tv_main_description.text = (weather.weather[i].description)

                    //iv_humidity.setImageURI(Uri.parse(weather.weather[0].icon))
                    tv_temp.text =
                        ("${weather.main.temp} " + getUnit(application.resources.configuration.toString()))
                    tv_humidity.text = ("${weather.main.humidity}%").toString()

                    //iv_min_max.setImageURI(Uri.parse(weather.weather[0].icon))
                    tv_min.text = ("Min: ${weather.main.tempMin} ºC").toString()
                    tv_max.text = ("Max: ${weather.main.tempMax} ºC").toString()

                    // iv_wind.setImageURI(Uri.parse(weather.weather[0].icon))
                    tv_speed.text = ("${weather.wind.speed}").toString()
                    tv_speed_unit.text = ("Miles/Hour").toString()

                    //  iv_location.setImageURI(Uri.parse(weather.weather[0].icon))
                    tv_name.text = (weather.name)
                    tv_country.text = (weather.sys.country)

                    tv_sunrise_end_time_id.text =
                        "Sunrise´s at: ${unixTime((weather.sys.sunrise).toLong())}"
                    tv_sunset_end_time_id.text =
                        "Sunset´s at: ${unixTime((weather.sys.sunset).toLong())}"

                    when (weather.weather[i].icon) {
                        "01d" -> iv_main.setImageResource(R.drawable.sunny)
                        "02d" -> iv_main.setImageResource(R.drawable.cloud)
                        "03d" -> iv_main.setImageResource(R.drawable.cloud)
                        "04d" -> iv_main.setImageResource(R.drawable.cloud)
                        "04n" -> iv_main.setImageResource(R.drawable.cloud)
                        "10d" -> iv_main.setImageResource(R.drawable.rain)
                        "11d" -> iv_main.setImageResource(R.drawable.storm)
                        "13d" -> iv_main.setImageResource(R.drawable.snowflake)
                        "01n" -> iv_main.setImageResource(R.drawable.cloud)
                        "02n" -> iv_main.setImageResource(R.drawable.cloud)
                        "03n" -> iv_main.setImageResource(R.drawable.cloud)
                        "10n" -> iv_main.setImageResource(R.drawable.cloud)
                        "11n" -> iv_main.setImageResource(R.drawable.rain)
                        "13n" -> iv_main.setImageResource(R.drawable.snowflake)
                    }


                }
            }
        }

    }

    private fun displayDataFromCityApi(weather: WeatherModel) {


        for (i in weather.weather.indices) {
            if (weather != null) {
                //  iv_main.setImageURI(Uri.parse(weather.weather[0].icon))
                tv_main.text = (weather.weather[i].main)
                tv_main_description.text = (weather.weather[i].description)

                //iv_humidity.setImageURI(Uri.parse(weather.weather[0].icon))
                tv_temp.text =
                    ("${weather.main.temp} " + getUnit(application.resources.configuration.toString()))
                tv_humidity.text = ("${weather.main.humidity}%").toString()

                //iv_min_max.setImageURI(Uri.parse(weather.weather[0].icon))
                tv_min.text = ("Min: ${weather.main.tempMin} ºC").toString()
                tv_max.text = ("Max: ${weather.main.tempMax} ºC").toString()

                // iv_wind.setImageURI(Uri.parse(weather.weather[0].icon))
                tv_speed.text = ("${weather.wind.speed}").toString()
                tv_speed_unit.text = ("Miles/Hour").toString()

                //  iv_location.setImageURI(Uri.parse(weather.weather[0].icon))
                tv_name.text = (weather.name)
                tv_country.text = (weather.sys.country)

                tv_sunrise_end_time_id.text =
                    "Sunrise´s at: ${unixTime((weather.sys.sunrise).toLong())}"
                tv_sunset_end_time_id.text =
                    "Sunset´s at: ${unixTime((weather.sys.sunset).toLong())}"

                when (weather.weather[i].icon) {
                    "01d" -> iv_main.setImageResource(R.drawable.sunny)
                    "02d" -> iv_main.setImageResource(R.drawable.cloud)
                    "03d" -> iv_main.setImageResource(R.drawable.cloud)
                    "04d" -> iv_main.setImageResource(R.drawable.cloud)
                    "04n" -> iv_main.setImageResource(R.drawable.cloud)
                    "10d" -> iv_main.setImageResource(R.drawable.rain)
                    "11d" -> iv_main.setImageResource(R.drawable.storm)
                    "13d" -> iv_main.setImageResource(R.drawable.snowflake)
                    "01n" -> iv_main.setImageResource(R.drawable.cloud)
                    "02n" -> iv_main.setImageResource(R.drawable.cloud)
                    "03n" -> iv_main.setImageResource(R.drawable.cloud)
                    "10n" -> iv_main.setImageResource(R.drawable.cloud)
                    "11n" -> iv_main.setImageResource(R.drawable.rain)
                    "13n" -> iv_main.setImageResource(R.drawable.snowflake)
                }

            }
        }
    }

    private fun getUnit(unit: String): String? {
        var valueUnit = "ºC"
        if (("US" == unit) || "LR" == unit || "MM" == unit) {
            valueUnit = "ºF"
        }
        return valueUnit
    }

    private fun unixTime(timex: Long): String? {
        val date = Date(timex * 1000L)
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_refresh, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.refresh_menu_item_id -> {
                getLocationWeatherDetails()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }


    }

    override fun onClick(view: View?) {
        when (view!!.id) {
            R.id.btn_searchCity_id -> {
                val cityName: String = tif_cityName_id.text.toString()
                if (cityName.isNotEmpty()) {
                    getWeatherFromCityName(cityName.trim())
                    tif_cityName_id.setText("")
                } else Snackbar.make(view, "Type the name of the city!", Snackbar.LENGTH_SHORT)
                    .setAction("Okay") {}.show()
            }
        }
    }
}