package scheme.claj.client;

import arc.Core;
import arc.struct.ArrayMap;
import arc.struct.ObjectMap;
import arc.util.Http;
import arc.util.serialization.Jval;


public class ClajServers {
  public static final String publicServersLink = 
      "https://github.com/xpdustry/claj/blob/main/public-servers.hjson?raw=true";
  public static final ArrayMap<String, String> online = new ArrayMap<>(),
                                               custom = new ArrayMap<>();
  
  public static synchronized void refreshOnline(Runnable done, arc.func.Cons<Throwable> failed) {
    // Public list
    Http.get(publicServersLink, result -> {
      Jval.JsonMap list = Jval.read(result.getResultAsString()).asObject();
      online.clear();
      for (ObjectMap.Entry<String, Jval> e : list)
        online.put(e.key, e.value.asString());
      done.run();
    }, failed);
    //online.put("Chaotic Neutral", "n3.xpdustry.com:7025");
  }
  
  @SuppressWarnings("unchecked")
  public static void loadCustom() {
    custom.clear();
    custom.putAll(Core.settings.getJson("claj-custom-servers", ArrayMap.class, String.class, ArrayMap::new));
  }
  
  public static void saveCustom() {
    Core.settings.putJson("claj-custom-servers", String.class, custom);
  }
}
