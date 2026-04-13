


mc-npc-mod: Expanding Kingdom Builder
Project Description

The
mc-npc-mod
introduces a new way to make Minecraft feel more alive
.
It overhauls village mechanics to facilitate the construction of an expanding kingdom, shifting the gameplay from a tedious babysitting effort to a comprehensive management simulation
.
Villages generate in the wild with new villagers and customizable housing buildings
.

Players can begin their kingdom by placing a
Treasury Block
, which opens a GUI to manage villager requests, physical storage, total villager lists, and kingdom settings
.
Potential Features
Autonomous Village and Kingdom Expansion
Customizable Villagers: Villagers use "New" villager entities with player bodies and configurable skins loaded from a drag-and-drop folder.
Specialized Villager Jobs: Villagers have defined types, such as miners, loggers, and fishermen, each corresponding to unique utility buildings.
Custom Building Templates: All roads, housing, and utility buildings are customizable via building data files that can be added or removed. Players can generate their own building templates using an easy, visual in-game tool and command.
Automatic Construction: Villagers automatically construct roads, housing, and utility buildings when the necessary resources are deposited into the village chest.
Villager Requests: Villagers actively make requests for buildings, such as new homes if they are homeless or a new utility building if they are jobless.
Job Mechanics and Simulation
Zoning Tool: Players use a Zoning tool to manually designate chunks for autonomous villager construction and work.
Anchor & Simulation Model: The Utility Building Block acts as the job manager, simulating work when the player is away.
Loaded State: The NPC is spawned and pathfinds between the Utility Building and the designated Zone, performing visual tasks (e.g., breaking blocks, swinging tools).
Unloaded State: When the player returns, the Utility Building calculates the resource generation based on time passed and the villager's WorkRate, instantly adding resources to the Kingdom Treasury.
Job-Specific World Impact:
Farmer: Automatically tills land, plants crops, and harvests yields in Agricultural Zones. It includes hooks for compatibility with other farming mods.
Quarryman: Physically removes blocks from the world in the Loaded state or generates a Cobblestone/Ore yield based on the zone's depth in the Passive state (Mining Zones).
Lumberjack: Chops down logs and automatically replants saplings to ensure sustainable forestry (Forestry Zones).
Gameplay Loop and Progression
NPC Schedule: Villagers follow a strict daily clock to make the world feel alive:
0600 - 0700: Commute to their Utility Building.
0700 - 1700: "On the Clock" work within their designated zone.
1700 - 1800: Social Hour near the village town square.
1800+: Return to their home and lock their claimed bed.
Funding Loop: Villagers "request" tools via the Treasury GUI. If the Treasury is out of necessary tools, the worker will stop performing their job, creating a gameplay loop that requires player funding.
Leveling System: Villagers gain a higher "WorkRate" as they work, allowing high-level (Master) villagers to yield double the resources compared to a Novice.
Environmental Management: Zones can become "depleted" by job-specific work, requiring the player to use the Zoning tool to manage and move workers to new resource areas.