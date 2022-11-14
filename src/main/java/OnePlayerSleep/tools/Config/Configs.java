package OnePlayerSleep.tools.Config;

public class Configs {
    public Config config;
    public Lang lang;
    public Worlds worlds;

    public Configs() {
        lang = new Lang();
        config = new Config();
        worlds = new Worlds();
    }
}
