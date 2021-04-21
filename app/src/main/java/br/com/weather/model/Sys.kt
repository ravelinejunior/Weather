package br.com.weather.model


import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class Sys(
    @SerializedName("country")
    val country: String, // BR
    @SerializedName("id")
    val id: Int, // 8329
    @SerializedName("sunrise")
    val sunrise: Int, // 1618823256
    @SerializedName("sunset")
    val sunset: Int, // 1618864898
    @SerializedName("type")
    val type: Int // 1
) : Serializable