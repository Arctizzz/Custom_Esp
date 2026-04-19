# Custom_Esp
A custom Esp for Hypixel Skyblock. 

# Commands:

/highlight add [String]
Add which name to scan for. This is case insensitive and checks if whatever filter you chose is contained. So "Zom" as filter would match all "Zombies" as Zombies contains Zom.

/highlight remove [String]
remove a filter from your list

/highlight list
Lists all your currently applied filters

/highlight scandelay [ticks] 
configure in which interval to scan. ~ 4 ticks is a good number in my experience but test what works for you. (this is pretty performance intense ofc)

/highlight color 
opens a gui to choose the esp color
/highlight color [RRGGBB] 
set the color directly

/highlight mode [outline/box/shaped]
Choose the type of highlighting. 
outline is simply a outline of the mob
box is the classic exp bounding box
shaped is an overlay over the mob (this is still a bit weird in some cases such as when a mob wears armor)

highlight depthcheck (true/false)
Legit esp = true | true Esp = false (cheat)

