package ru.kpfu.itis.net.message;

import ru.kpfu.itis.model.Bomb;
import ru.kpfu.itis.model.Explosion;
import ru.kpfu.itis.model.Player;
import ru.kpfu.itis.net.Protocol;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StateMessage extends Message {
    private final long tick;
    private final Map<Integer, Player> players;
    private final List<Bomb> bombs;
    private final List<Explosion> explosions;
    private final String grid;

    public StateMessage(long tick, Map<Integer, Player> players, List<Bomb> bombs, List<Explosion> explosions, String grid) {
        this.tick = tick;
        this.players = new HashMap<>(players);
        this.bombs = new ArrayList<>(bombs);
        this.explosions = new ArrayList<>(explosions);
        this.grid = grid;
    }

    public long tick() {
        return tick;
    }

    public Map<Integer, Player> players() {
        return new HashMap<>(players);
    }

    public List<Bomb> bombs() {
        return new ArrayList<>(bombs);
    }

    public List<Explosion> explosions() {
        return new ArrayList<>(explosions);
    }

    public String grid() {
        return grid;
    }

    @Override
    public MessageType getType() {
        return MessageType.STATE;
    }

    @Override
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append(MessageType.STATE.name()).append(Protocol.getDelimiter()).append(tick);
        for (Player p : players.values()) {
            sb.append(Protocol.getDelimiter()).append(Protocol.getTokenPlayer()).append(Protocol.getTokenDelimiter())
                    .append(p.id()).append(Protocol.getTokenDelimiter())
                    .append(p.x()).append(Protocol.getTokenDelimiter())
                    .append(p.y()).append(Protocol.getTokenDelimiter())
                    .append(p.alive()).append(Protocol.getTokenDelimiter())
                    .append(p.bombsAvailable()).append(Protocol.getTokenDelimiter())
                    .append(p.name());
        }
        for (Bomb b : bombs) {
            sb.append(Protocol.getDelimiter()).append(Protocol.getTokenBomb()).append(Protocol.getTokenDelimiter())
                    .append(b.ownerId()).append(Protocol.getTokenDelimiter())
                    .append(b.x()).append(Protocol.getTokenDelimiter())
                    .append(b.y()).append(Protocol.getTokenDelimiter())
                    .append(b.timer());
        }
        for (Explosion e : explosions) {
            sb.append(Protocol.getDelimiter()).append(Protocol.getTokenExplosion()).append(Protocol.getTokenDelimiter())
                    .append(e.x()).append(Protocol.getTokenDelimiter())
                    .append(e.y()).append(Protocol.getTokenDelimiter())
                    .append(e.ttl());
        }
        sb.append(Protocol.getDelimiter()).append(Protocol.getTokenMap()).append(Protocol.getTokenDelimiter()).append(grid);
        return sb.toString();
    }
}
