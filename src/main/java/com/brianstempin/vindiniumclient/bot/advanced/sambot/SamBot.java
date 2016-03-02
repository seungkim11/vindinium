package com.brianstempin.vindiniumclient.bot.advanced.sambot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.brianstempin.vindiniumclient.bot.BotMove;
import com.brianstempin.vindiniumclient.bot.advanced.AdvancedBot;
import com.brianstempin.vindiniumclient.bot.advanced.AdvancedGameState;
import com.brianstempin.vindiniumclient.bot.advanced.Mine;
import com.brianstempin.vindiniumclient.bot.advanced.Pub;
import com.brianstempin.vindiniumclient.bot.advanced.Vertex;
import com.brianstempin.vindiniumclient.dto.GameState;
import com.brianstempin.vindiniumclient.dto.GameState.Hero;
import com.brianstempin.vindiniumclient.dto.GameState.Position;

public class SamBot implements AdvancedBot {

	private static final Logger logger = LogManager.getLogger(SamBot.class);

	private Map<GameState.Position, Mine> mines;
	private Map<GameState.Position, Pub> pubs;
	private Map<GameState.Position, GameState.Hero> heroesByPosition;
	private Map<Integer, GameState.Hero> heroesById;
	private Map<GameState.Position, Vertex> boardGraph;
	private GameState.Hero me;
	List<GameState.Hero> enemies;
	private boolean healing;

	public static class Node implements Comparable<Node> {
		Vertex vertex;
		double data;

		public Node(Vertex vertex, double data) {
			this.vertex = vertex;
			this.data = data;
		}

		public void setData(double data) {
			this.data = data;
		}

		public double getData() {
			return this.data;
		}

		public Vertex getVertex() {
			return this.vertex;
		}

		public Position getPosition() {
			return this.vertex.getPosition();
		}

		@Override
		public int compareTo(Node other) {
			return (int) Math.ceil(this.data - other.data) * 10;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof Node))
				return false;
			Node other = (Node) o;
			return this.getPosition().equals(other.getPosition());
		}

		@Override
		public int hashCode() {
			return this.getPosition().hashCode();
		}

		public String toString() {
			String str = "";
			str += "vertex: " + vertex + ", data: " + data;
			return str;
		}
	}
	
	// this is for going around path if hero is on the way
	public List<BotMove> AStarWeak(Vertex source, Vertex dist) {
		PriorityQueue<Node> open = new PriorityQueue<>();
		Map<Node, Double> distanceMemory = new HashMap<>();
		Set<Node> closed = new HashSet<>();
		Map<Node, Node> parents = new HashMap<>();
		open.add(new Node(source, 0));
		Node distNode = new Node(dist, 0);
		while (!open.isEmpty()) {
			Node u = open.poll();
			closed.add(u);
			// if u is the destination, break;
			if (u.getVertex().getPosition().equals(dist.getPosition())) {
				distNode = u;
				break;
			}
			for (Vertex v : u.getVertex().getAdjacentVertices()) {
				if (!closed.contains(new Node(v, 0))) {

					if (heroAdjacent(v)) continue; // hero is adjacent, dont go this path 
					
					double g = u.getData() + 1;
					double h = getDistance(v.getPosition(), dist.getPosition());
					double f = h + g;
					Node n = new Node(v, f);
					if (distanceMemory.containsKey(n)) {
						if (distanceMemory.get(n) > f) {
							distanceMemory.put(n, f);
							open.remove(n);
							open.add(n);
							parents.put(n, u);
						}
					} else {
						distanceMemory.put(n, f);
						parents.put(n, u);
						open.add(n);
					}
				}
			}
		}
		List<BotMove> moves = getTrace(distNode, parents);
		return moves;

	}
	
	public List<BotMove> AStarAggressive(Vertex source, Vertex dist) {
		PriorityQueue<Node> open = new PriorityQueue<>();
		Map<Node, Double> distanceMemory = new HashMap<>();
		Set<Node> closed = new HashSet<>();
		Map<Node, Node> parents = new HashMap<>();
		open.add(new Node(source, 0));
		Node distNode = new Node(dist, 0);
		while (!open.isEmpty()) {
			Node u = open.poll();
			closed.add(u);
			// if u is the destination, break;
			if (u.getVertex().getPosition().equals(dist.getPosition())) {
				distNode = u;
				break;
			}
			for (Vertex v : u.getVertex().getAdjacentVertices()) {
				if (!closed.contains(new Node(v, 0))) {
					double g = u.getData() + 1;
					double h = getDistance(v.getPosition(), dist.getPosition());
					double f = h + g;
					Node n = new Node(v, f);
					if (distanceMemory.containsKey(n)) {
						if (distanceMemory.get(n) > f) {
							distanceMemory.put(n, f);
							open.remove(n);
							open.add(n);
							parents.put(n, u);
						}
					} else {
						distanceMemory.put(n, f);
						parents.put(n, u);
						open.add(n);
					}
				}
			}
		}
		List<BotMove> moves = getTrace(distNode, parents);
		return moves;
	}
	//trace method for Astar search
	public List<BotMove> getTrace(Node dist, Map<Node, Node> parents) {

		List<BotMove> moves = new ArrayList<>();
		Node backTrack = dist;
		while (backTrack != null) {
			Node parent = parents.get(backTrack);
			if (parent != null) {
				int parentX = parent.getPosition().getX();
				int parentY = parent.getPosition().getY();
				int currentX = backTrack.getPosition().getX();
				int currentY = backTrack.getPosition().getY();
				if (currentX == parentX) {
					if (parentY < currentY) {
						moves.add(0, BotMove.EAST); 
					} else {
						moves.add(0, BotMove.WEST); 
					}
				} else {
					if (parentX < currentX) {
						moves.add(0, BotMove.SOUTH);
					} else {
						moves.add(0, BotMove.NORTH);
					}
				}
			}
			backTrack = parent;
		}
		return moves;
	}

	@Override
	public BotMove move(AdvancedGameState gameState) {
		mines = gameState.getMines();
		pubs = gameState.getPubs();
		heroesByPosition = gameState.getHeroesByPosition();
		heroesById = gameState.getHeroesById();
		boardGraph = gameState.getBoardGraph();
		me = gameState.getMe();
		
		enemies = new ArrayList<>();
		for (Hero h : heroesById.values()) {
			if (h.getId() != me.getId()) enemies.add(h);
		}
		
		Mine closestMine = getClosestMine();
		Pub closestPub = getClosestPub();
		Pub freePub = getFreePub();
			
		List<BotMove> moves = new ArrayList<>();
		
		if (me.getLife() >= 90)	healing = false;
				
		if (isWinning()) {
			// turtle mode
			if (me.getLife() > 50 && nextToPub(me)) {
				return BotMove.STAY;
			}
			moves = AStarWeak(boardGraph.get(me.getPos()), boardGraph.get(closestPub.getPosition()));
		}else if (me.getLife() > 25){
			if (healing) {
				moves = AStarWeak(boardGraph.get(me.getPos()), boardGraph.get(closestPub.getPosition()));
			}else{
				Hero near = getHeroNearMe();
				if (near != null){
					// if hero is worthy of attacking (one shot kill or (has mines & not crashed & not camping near pub))
					if (near.getLife() <= 20 || (!heroUnworthyOfAttack(near) && near.getLife() < me.getLife())){ // chase after him
						moves = AStarAggressive(boardGraph.get(me.getPos()), boardGraph.get(near.getPos()));
					}
				}else{
					// just get mines
					if (me.getLife() >= 60){
						moves = AStarAggressive(boardGraph.get(me.getPos()), boardGraph.get(closestMine.getPosition()));
					}else{
						moves = AStarWeak(boardGraph.get(me.getPos()), boardGraph.get(closestMine.getPosition()));
					}
				}
			}
		} else {
			healing = true;
			moves = AStarWeak(boardGraph.get(me.getPos()), boardGraph.get(closestPub.getPosition()));
		}

		if (!moves.isEmpty()) {
			return moves.get(0);
		} else {
			// no path, then 
			if (me.getLife() > 50 && nextToPub(me)) {
				return BotMove.STAY;
			}
			if (healing){
				moves = AStarWeak(boardGraph.get(me.getPos()), boardGraph.get(freePub.getPosition()));
			}else{
				moves = AStarAggressive(boardGraph.get(me.getPos()), boardGraph.get(closestMine.getPosition()));
			}
			
			return moves.get(0);
		}
	}
	
	public Mine getClosestMine(){
		double closestDistance = Math.sqrt(boardGraph.size() * boardGraph.size());
		Mine closestMine = null;
		for (Mine m : mines.values()) {
			if (m.getOwner() == null || m.getOwner().getId() != me.getId()) {
				double distance = getDistance(me.getPos(), m.getPosition());
				if (distance < closestDistance) {
					closestDistance = distance;
					closestMine = m;
				}
			}	
		}
		return closestMine;
	}
	
	public Pub getClosestPub(){
		double closestDistance = Math.sqrt(boardGraph.size() * boardGraph.size());
		Pub closestPub = null;
		for (Pub p : pubs.values()) {
			double distance = getDistance(me.getPos(), p.getPosition());
			if (distance < closestDistance) {
				closestDistance = distance;
				closestPub = p;
			}
		}
		return closestPub;
	}
	
	public Pub getFreePub(){
		double closestDistance = Math.sqrt(boardGraph.size() * boardGraph.size());
		Pub myPub = null;
		for (Pub p : pubs.values()) {
			double distance = getDistance(me.getSpawnPos(), p.getPosition());
			if (distance < closestDistance && !pubOccupied(p)) {
				closestDistance = distance;
				myPub = p;
			}
		}
		return myPub;
	}
	
	public boolean pubOccupied(Pub pub){
		Vertex pubVertex = boardGraph.get(pub.getPosition());
		for (Vertex n: pubVertex.getAdjacentVertices()){
			if (heroAdjacent(n)) return true; 
		}
		return false;
	}
	
	public boolean nextToPub(Hero h) {
		for (Pub p : pubs.values()) {
			if (getDistance(p.getPosition(), h.getPos()) == 1)
				return true;
		}
		return false;
	}

	public boolean isWinning() {
		boolean winning = true;

		for (Hero h : enemies) {
			if (h.getMineCount() >= me.getMineCount()) winning = false;
			if (h.getGold() >= me.getGold()) winning = false;
		}
		return winning;
	}
	
	
	
	public Hero getHeroNearMe(){
		for (Hero h: enemies){
			if (getDistance(h.getPos(), me.getPos()) <= 2){
				return h;			
			}
		}
		return null;
	}
	
	public boolean heroUnworthyOfAttack(Hero hero){
		// hero has no mines
		if (hero.getMineCount() == 0) return true;
		
		// hero is crashed
		if (hero.isCrashed()) return true;
		
		// hero is camping near the pub
		if (nextToPub(hero)) return true;
				
		return false;
	}
	
	public boolean heroAdjacent(Vertex v) {
		// check vertex and its adjacent, and avoid
		for (GameState.Hero hero : enemies) {
			if (hero.getPos().equals(v.getPosition()) || hero.getSpawnPos().equals(v.getPosition()))
				return true;
		}
		for (Vertex n : v.getAdjacentVertices()) {
			for (GameState.Hero hero : enemies) {
				if (hero.getPos().equals(n.getPosition()) || hero.getSpawnPos().equals(n.getPosition()))
					return true;
			}
		}
		return false;
	}

	@Override
	public void setup() {
		// TODO Auto-generated method stub

	}

	@Override
	public void shutdown() {
		// TODO Auto-generated method stub

	}

	public double getDistance(Position p1, Position p2) {
		int dx = Math.abs(p1.getX() - p2.getX());
		int dy = Math.abs(p1.getY() - p2.getY());
		return Math.sqrt(dx * dx + dy * dy);
	}

	public String getMovesString(List<BotMove> moves) {
		String str = "";
		for (BotMove b : moves) {
			str += b.toString() + " -> ";
		}
		return str;
	}
}
