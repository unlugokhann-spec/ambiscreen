package com.ambiscreen.app.capture

enum class Edge { TOP, RIGHT, BOTTOM, LEFT }

data class EdgeConfig(
    val enabled: Boolean = true,
    val ledCount: Int = 0,
)

/**
 * Fiziksel LED şeridinin ekran çevresindeki yerleşimini tanımlar. Her kenarın
 * kendi LED sayısı ve açık/kapalı durumu vardır; bu sayede tam perimetre
 * (4 kenar), yalnızca üst-alt, L-şekilli (örn. üst+sol) veya tek kenar gibi
 * kurulumlar desteklenir. `startEdge`, LED şeridinin fiziksel olarak hangi
 * kenardan başladığını belirtir; açık kenarlar bu noktadan saat yönünde
 * sırayla dizilip DDP'ye o sırayla gönderilir.
 */
data class LedLayoutConfig(
    val startEdge: Edge = Edge.TOP,
    val top: EdgeConfig = EdgeConfig(true, 20),
    val right: EdgeConfig = EdgeConfig(true, 10),
    val bottom: EdgeConfig = EdgeConfig(true, 20),
    val left: EdgeConfig = EdgeConfig(true, 10),
) {
    fun configFor(edge: Edge): EdgeConfig = when (edge) {
        Edge.TOP -> top
        Edge.RIGHT -> right
        Edge.BOTTOM -> bottom
        Edge.LEFT -> left
    }

    fun with(edge: Edge, config: EdgeConfig): LedLayoutConfig = when (edge) {
        Edge.TOP -> copy(top = config)
        Edge.RIGHT -> copy(right = config)
        Edge.BOTTOM -> copy(bottom = config)
        Edge.LEFT -> copy(left = config)
    }

    /** Saat yönünde, startEdge'den başlayarak yalnızca açık ve LED'i olan kenarları sıralar. */
    fun wiringOrder(): List<Edge> {
        val canonical = listOf(Edge.TOP, Edge.RIGHT, Edge.BOTTOM, Edge.LEFT)
        val startIndex = canonical.indexOf(startEdge).coerceAtLeast(0)
        val rotated = canonical.subList(startIndex, canonical.size) + canonical.subList(0, startIndex)
        return rotated.filter { configFor(it).enabled && configFor(it).ledCount > 0 }
    }

    fun totalLedCount(): Int = wiringOrder().sumOf { configFor(it).ledCount }

    fun serialize(): String {
        fun encode(cfg: EdgeConfig) = "${if (cfg.enabled) 1 else 0}:${cfg.ledCount}"
        return "${startEdge.name}|${encode(top)}|${encode(right)}|${encode(bottom)}|${encode(left)}"
    }

    companion object {
        val DEFAULT = LedLayoutConfig()

        fun deserialize(raw: String?): LedLayoutConfig {
            if (raw.isNullOrBlank()) return DEFAULT
            return try {
                val parts = raw.split("|")
                fun parse(part: String): EdgeConfig {
                    val (enabledFlag, count) = part.split(":")
                    return EdgeConfig(enabledFlag == "1", count.toInt())
                }
                LedLayoutConfig(
                    startEdge = Edge.valueOf(parts[0]),
                    top = parse(parts[1]),
                    right = parse(parts[2]),
                    bottom = parse(parts[3]),
                    left = parse(parts[4]),
                )
            } catch (e: Exception) {
                DEFAULT
            }
        }
    }
}
