package scheme.tools;

import arc.Events;
import arc.util.serialization.JsonReader;
import arc.util.serialization.JsonValue;
import mindustry.Vars;
import mindustry.game.EventType;

public class ServerUtils {
    String lastServer;

    public void init(){
        Events.on(EventType.ClientServerConnectEvent.class, event -> lastServer = event.ip + ":" + event.port);
    }

    public boolean serverNameEqual(String name){
        if(!Vars.net.client() || lastServer == null) return false;

        try{
            JsonValue servers = new JsonReader().parse(Vars.serverCacheFile);
            for(JsonValue server = servers.child; server != null; server = server.next){
                String ip = server.getString("ip", "");
                int port = server.getInt("port", Vars.port);
                if(lastServer.equals(ip + ":" + port)){
                    return name.equals(server.getString("name", null));
                }
            }
        }catch(Exception ignored){}

        return false;
    }
}
