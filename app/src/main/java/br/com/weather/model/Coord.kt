package br.com.weather.model


import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class Coord(
    @SerializedName("lat")
    val lat: Double, // -19.9208
    @SerializedName("lon")
    val lon: Double // -43.9378
): Serializable