package br.com.weather.model


import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class Wind(
    @SerializedName("deg")
    val deg: Int, // 166
    @SerializedName("gust")
    val gust: Double, // 2.28
    @SerializedName("speed")
    val speed: Double // 1.71
): Serializable