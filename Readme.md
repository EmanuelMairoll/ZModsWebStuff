# ZMods Web Stuff
This is the server backend powering my `ZMods Client Hacks` for the legit PvP Client "LabyMod".

It allows you to install the Hacks from the Mod Download Screen by careful injection into the traffic flow.
The System consists of two modules:

## ZMods Server
The Server acts as a proxy to the LabyMod-API. On the one hand it rewrites the API call to the Mod list to inject the Hacks, on the other hand it serves the requested jar-files. As the system is designed to run on a Heroku-Instance, it uses the Dropbox API for persistence and only caches the jar-files locally.
Furthermore, it intercepts all LabyMod-Updates rewriting all API calls in the Labymod.jar to this proxy using ASM Bytecode Manipulation, to ensure, that future versions of the Client also acces the Proxy instead of the API directly.

##ZMods Patcher
The Patcher is the update-rewriting counterpart on the Client side and serves as an "entry point" into the proxied API cycle. It is a simple GUI Application prompting you to select the local Labymod.jar for first-time rewriting the API calls.

##Production
The Server is hosted on Heroku and can be found at `zmods.herokuapp.com`. 

For example, the modified addons.json can be found [here](http://zmods.herokuapp.com/labymod/addons.json).

The patcher can be compiled and used "as is".
