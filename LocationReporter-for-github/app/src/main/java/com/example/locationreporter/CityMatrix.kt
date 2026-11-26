package com.example.locationreporter

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 打卡点白名单:手机定位落在某打卡点 50km 内时,直接用该预设坐标与地址打卡;
 * 50km 以外(或未配置坐标的城市)走真实定位 + 高德逆地理编码地址。
 */
object CityMatrix {

    data class Spot(val lng: Double, val lat: Double, val address: String)
    data class Match(val city: String, val spot: Spot, val distanceKm: Double)

    const val RADIUS_KM = 50.0

    // 与原 Python 脚本 City_Matrix 一致;值为 null 的城市(合肥/贵阳/太原)不参与匹配
    private val SPOTS: Map<String, Spot?> = linkedMapOf(
        "西安" to Spot(108.812452, 34.170214, "陕西省西安市长安区郭杜街道上林苑五路西安高新技术产业开发区"),
        "天津" to Spot(116.982821, 39.418861, "天津市武清区湖景园慧源道"),
        "武汉" to Spot(114.344789, 30.553912, "湖北省武汉市武昌区特变电工(新能源公司系统集成华中公司)"),
        "合肥" to null,
        "内蒙" to Spot(111.759207, 40.815198, "内蒙古自治区呼和浩特市赛罕区金隅环球中心(公交站)a座"),
        "兰州" to Spot(103.772806, 36.068345, "甘肃省兰州市七里河区建兰大厦"),
        "成都" to Spot(104.066331, 30.550628, "四川省成都市武侯区成都市高新区天府二街99号"),
        "贵阳" to null,
        "阜平" to Spot(114.350560, 38.822690, "河北省保定市阜平县王林口镇"),
        "青海" to Spot(101.710583, 36.643052, "青海省西宁市城西区财富广场A座"),
        "乌鲁木齐" to Spot(87.581677, 43.873648, "新疆维吾尔自治区乌鲁木齐市新市区长春南路399号"),
        "太原" to null,
        "北京" to Spot(116.290908, 39.822317, "北京市丰台区特变电工"),
        "济南" to Spot(117.106269, 36.659819, "山东省济南市历下区经十路10863号")
    )

    /** 返回 50km 内最近的预设打卡点;没有则 null。 */
    fun match(lng: Double, lat: Double): Match? {
        var best: Match? = null
        for ((name, spot) in SPOTS) {
            if (spot == null) continue
            val d = distanceKm(lat, lng, spot.lat, spot.lng)
            if (d <= RADIUS_KM && (best == null || d < best.distanceKm)) {
                best = Match(name, spot, d)
            }
        }
        return best
    }

    /** Haversine 球面距离,单位 km。 */
    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(sqrt(a))
    }
}
