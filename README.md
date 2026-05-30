# ASPA 
### (Advanced Server Performance Analysis)

ASPA is a Spigot plugin that turns your server into a performance analytics platform. It continuously captures live health metrics, player session behavior, and long-term trends, then serves a built-in web dashboard and REST API for actionable insights.

This is a beta (snapshot) release. Expect rapid iteration, incomplete features, and potential breaking changes.

## Contributions
**Contributions to development are welcome. Feel free to me out on GitHub.**

## Key Features
- Live server health: TPS, MSPT, CPU, RAM, GC, ping, entities, and loaded chunks
- Historical metrics with anomaly detection and correlated factor analysis
- Player analytics: retention cohorts, punchcard heatmaps, geographic distribution, and session stats
- Forecasting engine for peak player activity windows
- Embedded web dashboard with authentication and user/role permissions
- Optional Pterodactyl integration (power actions, console commands, backups)
- Flexible storage: SQLite, MySQL, or MongoDB
- GeoIP support (MaxMind) for country/timezone insights

## How It Works
ASPA runs as a single plugin that collects metrics at a configurable interval, stores them in a database, and exposes them through a local HTTP server. The web UI is served directly from the plugin, so no separate web host is required.

## Commands and Permissions
- /aspa [reload|status|token]
- Permission: aspa.admin (default: op)

## Setup Notes
1. Install the plugin and start the server once to generate the default config.
2. Configure your database and API token in config.yml.
3. (Optional) Add GeoLite2-City.mmdb to the plugin data folder for GeoIP.
4. (Optional) Enable Pterodactyl integration and set credentials.

## Compatibility
- Spigot/Paper 1.20+
- Java 21+

## Beta Disclaimer
This snapshot build is intended for testing and feedback purposes. Please ensure you use backups, as configuration or database changes are to be expected as the project matures. 
Note that this description may not always reflect the current state of development.

