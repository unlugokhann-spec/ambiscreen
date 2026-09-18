"""
Android tarafındaki `LedLayoutConfig` (Kotlin) ile birebir aynı modelin
Python portu. Her kenarın kendi LED sayısı ve açık/kapalı durumu vardır;
bu sayede tam perimetre, yalnızca üst-alt veya L-şekilli gibi kurulumlar
desteklenir.
"""
from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum


class Edge(Enum):
    TOP = "TOP"
    RIGHT = "RIGHT"
    BOTTOM = "BOTTOM"
    LEFT = "LEFT"


@dataclass
class EdgeConfig:
    enabled: bool = True
    led_count: int = 0


@dataclass
class LedLayoutConfig:
    start_edge: Edge = Edge.TOP
    top: EdgeConfig = field(default_factory=lambda: EdgeConfig(True, 20))
    right: EdgeConfig = field(default_factory=lambda: EdgeConfig(True, 10))
    bottom: EdgeConfig = field(default_factory=lambda: EdgeConfig(True, 20))
    left: EdgeConfig = field(default_factory=lambda: EdgeConfig(True, 10))

    def config_for(self, edge: Edge) -> EdgeConfig:
        return {
            Edge.TOP: self.top,
            Edge.RIGHT: self.right,
            Edge.BOTTOM: self.bottom,
            Edge.LEFT: self.left,
        }[edge]

    def wiring_order(self) -> list[Edge]:
        canonical = [Edge.TOP, Edge.RIGHT, Edge.BOTTOM, Edge.LEFT]
        start_index = canonical.index(self.start_edge)
        rotated = canonical[start_index:] + canonical[:start_index]
        return [e for e in rotated if self.config_for(e).enabled and self.config_for(e).led_count > 0]

    def total_led_count(self) -> int:
        return sum(self.config_for(e).led_count for e in self.wiring_order())

    @staticmethod
    def from_dict(data: dict | None) -> "LedLayoutConfig":
        data = data or {}

        def edge_cfg(key: str, default_enabled: bool, default_count: int) -> EdgeConfig:
            d = data.get(key, {})
            return EdgeConfig(d.get("enabled", default_enabled), d.get("led_count", default_count))

        return LedLayoutConfig(
            start_edge=Edge(data.get("start_edge", "TOP")),
            top=edge_cfg("top", True, 20),
            right=edge_cfg("right", True, 10),
            bottom=edge_cfg("bottom", True, 20),
            left=edge_cfg("left", True, 10),
        )
