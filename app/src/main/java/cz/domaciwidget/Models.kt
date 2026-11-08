package cz.domaciwidget

data class Session(val userId: String, val ssecurity: String, val serviceToken: String)

data class Device(val did: String, val name: String, val model: String, val specType: String)

data class Reading(val temp: Double?, val hum: Double?, val bat: Int?, val ts: Long)
