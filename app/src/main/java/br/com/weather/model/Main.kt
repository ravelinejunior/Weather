package br.com.weather.model


import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class Main(
    @SerializedName("feels_like")
    val feelsLike: Double, // 295.4
    @SerializedName("humidity")
    val humidity: Int, // 64
    @SerializedName("pressure")
    val pressure: Double, // 1015
    @SerializedName("temp")
    val temp: Double, // 295.44
    @SerializedName("temp_max")
    val tempMax: Double, // 296.15
    @SerializedName("temp_min")
    val tempMin: Double,// 295.15
    @SerializedName("sea_level")
    val seaLevel: Double,
    @SerializedName("grnd_level")
    val groundLevel: Double
): Serializable