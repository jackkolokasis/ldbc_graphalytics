package org.tudelft.graphalytics.giraph.evo;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.hadoop.io.Writable;

public class ForestFireModelData implements Writable {

	public static enum ForestFireModelState {
		ALIVE,
		BURNING,
		BURNED
	}
	
	private long[] inEdges;
	private Map<Long, ForestFireModelState> statePerInstigator;
	
	/** Required for instantiation using the Writable interface. Do not use. */
	public ForestFireModelData() {
		inEdges = new long[0];
		statePerInstigator = new HashMap<>();
	}
	
	private ForestFireModelData(long[] inEdges) {
		this.inEdges = inEdges;
		this.statePerInstigator = new HashMap<>();
	}
	
	public long[] getInEdges() {
		return inEdges;
	}
	
	public Set<Map.Entry<Long, ForestFireModelState>> getStates() {
		return statePerInstigator.entrySet();
	}
	public ForestFireModelState getState(long instigatorId) {
		if (statePerInstigator.containsKey(instigatorId))
			return statePerInstigator.get(instigatorId);
		return ForestFireModelState.ALIVE;
	}
	public Set<Long> getInstigatorIds() {
		return statePerInstigator.keySet();
	}
	public void setState(long instigatorId, ForestFireModelState newState) {
		statePerInstigator.put(instigatorId, newState);
	}
	
	@Override
	public void write(DataOutput out) throws IOException {
		// Store the incoming edges of this vertex
		out.writeInt(inEdges.length);
		for (int i = 0; i < inEdges.length; i++) {
			out.writeLong(inEdges[i]);
		}
		// Store the forest fire state of this vertex with respect to various sources
		out.writeInt(statePerInstigator.size());
		for (Map.Entry<Long, ForestFireModelState> state : statePerInstigator.entrySet()) {
			out.writeLong(state.getKey());
			out.writeByte(state.getValue().ordinal());
		}
	}

	@Override
	public void readFields(DataInput in) throws IOException {
		// Read the incoming edges
		int length = in.readInt();
		inEdges = new long[length];
		for (int i = 0; i < length; i++) {
			inEdges[i] = in.readLong();
		}
		// Read the forest fire states
		length = in.readInt();
		statePerInstigator = new HashMap<>();
		for (int i = 0; i < length; i++) {
			long id = in.readLong();
			ForestFireModelState state = ForestFireModelState.values()[in.readByte()];
			statePerInstigator.put(id, state);
		}
	}

	public static ForestFireModelData fromInEdges(Collection<Long> inEdges) {
		long[] inEdgeArray = new long[inEdges.size()];
		int i = 0;
		for (Long inEdge : inEdges)
			inEdgeArray[i++] = inEdge;
		return new ForestFireModelData(inEdgeArray);
	}
	
}