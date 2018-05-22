/*
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2011, 2014  
*/
package com.ibm.streamsx.inet.window;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ibm.streams.operator.Attribute;
import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.StreamingInput;
import com.ibm.streams.operator.Tuple;
import com.ibm.streams.operator.window.StreamWindow;
import com.ibm.streams.operator.window.StreamWindowEvent;
import com.ibm.streams.operator.window.StreamWindowListener;
import com.ibm.streams.operator.window.StreamWindowPartitioner;
import com.ibm.streams.operator.window.WindowUtilities;

/**
 * Window listener that provides a view of the window's
 * contents at the last trigger for sliding windows, or
 * eviction for tumbling windows.
 *
 * @param <T>
 */
public class WindowContentsAtTrigger<T> implements StreamWindowListener<T> {
	
	private final OperatorContext context;
	private final StreamingInput<T> input;
	
	private final boolean isSliding;
	
	private final Map<Object,List<T>> windowContents =
		Collections.synchronizedMap(new HashMap<Object,List<T>>());
	
	private final List<Attribute> partitionAttributes;
	
	private long lastModified = System.currentTimeMillis();

	@SuppressWarnings("unchecked")
	public WindowContentsAtTrigger(OperatorContext context, StreamingInput<T> input) {
		this.context = context;
		this.input = input;
		isSliding = StreamWindow.Type.SLIDING.equals(input.getStreamWindow().getType());
		List<String> partitionKeys = null;
		//Tokenize paritionKey if cardinality is 1 and put all values to partitionKeys
		List<String> primaryPartitionKeys = context.getParameterValues("partitionKey");
		if (primaryPartitionKeys.size() == 1) {
			String[] stringArr = primaryPartitionKeys.get(0).split(",");
			if (stringArr.length > 1) {
				partitionKeys = new ArrayList<String>();
				for (int i = 0; i < stringArr.length; i++) {
					partitionKeys.add(stringArr[i]);
				}
			} else {
				partitionKeys = primaryPartitionKeys;
			}
		} else {
			partitionKeys = primaryPartitionKeys;
		}
		if (!partitionKeys.isEmpty()) {
		    if (!input.getStreamWindow().isPartitioned())
		        throw new IllegalStateException("Input port " + input.getName() + "is not partitioned");
		    
		    
		    if (partitionKeys.size() == 1) {
		    WindowUtilities.registerAttributePartitioner(	            
		            (StreamWindow<Tuple>) input.getStreamWindow(),
		            partitionKeys.toArray(new String[0]));
		    } else {
		     // RTC 14070
		        // Multiple attributes.
		        final int[] indexes = new int[partitionKeys.size()];
		        for (int i = 0; i < indexes.length; i++)
		            indexes[i] = input.getStreamSchema().getAttributeIndex(partitionKeys.get(i));
		        
		        ((StreamWindow<Tuple>) input.getStreamWindow()).registerPartitioner(new StreamWindowPartitioner<Tuple,List<Object>>() {

		            @Override
		            public List<Object> getPartition(Tuple tuple) {
		                final List<Object> attrs = new ArrayList<Object>(indexes.length);
		                for (int i = 0; i < indexes.length; i++)
		                    attrs.add(tuple.getObject(indexes[i]));
		                return Collections.unmodifiableList(attrs);
		            }
		            
		        });
		    }
		    
		    List<Attribute> pa = new ArrayList<Attribute>();
		    for (String attributeName : partitionKeys)
		        pa.add(input.getStreamSchema().getAttribute(attributeName));
		    partitionAttributes = Collections.unmodifiableList(pa);
		} else {
            if (input.getStreamWindow().isPartitioned())
                throw new IllegalStateException("Input port " + input.getName() + "is partitioned but partitionKey parameter is not set.");
            partitionAttributes = Collections.emptyList();	    
		}

	}

	@Override
	public synchronized void handleEvent(final StreamWindowEvent<T> event) throws Exception {
		final Object partition = event.getPartition();
		switch (event.getType()) {
		case EVICTION:
			if (isSliding)
				break;
			// fall through for a tumbling window
		case TRIGGER:
			List<T> tuples = new ArrayList<T>();
			for (T tuple : event.getTuples())
				tuples.add(tuple);
			if (tuples.isEmpty())
				windowContents.remove(partition);
			else
			    windowContents.put(partition, tuples);
			lastModified = System.currentTimeMillis();
			break;
		case PARTITION_EVICTION:
			windowContents.remove(partition);
			lastModified = System.currentTimeMillis();
			break;
		default:
			break;
			
		}
	}
	
	public List<T> getWindowContents(Object partition) {
	    if (partition == null)
	        return getAllPartitions();
	    
		List<T> tuples = windowContents.get(partition);
		if (tuples == null)
			return Collections.emptyList();
		return Collections.unmodifiableList(tuples);
	}
	
	private List<T> getAllPartitions() {
	    List<T> allTuples = new ArrayList<T>();
	    synchronized (windowContents) {
	        for (List<T> tuples : windowContents.values()) {
	            allTuples.addAll(tuples);
	        }
	    }
	    return allTuples;
	}

	public OperatorContext getContext() {
		return context;
	}

	public StreamingInput<T> getInput() {
		return input;
	}

    public List<Attribute> getPartitionAttributes() {
        return partitionAttributes;
    }
}
