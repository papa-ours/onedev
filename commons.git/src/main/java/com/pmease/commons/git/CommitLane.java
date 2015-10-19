package com.pmease.commons.git;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import com.google.common.base.Preconditions;

@SuppressWarnings("serial")
public class CommitLane implements Serializable {
	
	/*
	 * Each list item represents a row, and each row represents a map of 
	 * line to its drawn column at this row. With this info, one can 
	 * go through each row to draw every child->parent line. Taking 
	 * line (2,4) for instance, we first find commit column at row 2 
	 * (by looking up the special line (2,2)), then find its column 
	 * at row 3, and then find commit column at row 4 (by looking up the 
	 * special line (4,4)). Finally connect these columns we got the 
	 * line drawn.
	 */
	private final List<Map<Line, Integer>> rows = new ArrayList<>();
	
	public CommitLane(List<Commit> commits, int maxColumns) {
		Preconditions.checkArgument(!commits.isEmpty() && maxColumns>=1);
		
		Map<String, Integer> mapOfHashToRow = new HashMap<>();
		Map<Integer, List<Integer>> mapOfParentToChildren = new HashMap<>(); 
		for (int i=0; i<commits.size(); i++) {
			Commit commit = commits.get(i);
			mapOfHashToRow.put(commit.getHash(), i);
		}
		for (int i=0; i<commits.size(); i++) {
			Commit commit = commits.get(i);
			for (String parentHash: commit.getParentHashes()) {
				Integer parent = mapOfHashToRow.get(parentHash);
				if (parent != null) {
					List<Integer> children = mapOfParentToChildren.get(parent);
					if (children == null) {
						children = new ArrayList<>();
						mapOfParentToChildren.put(parent, children);
					}
					children.add(i);
				}
			}
		}
		
		for (int rowIndex=0; rowIndex<commits.size(); rowIndex++) {
			Map<Line, Integer> row = new LinkedHashMap<>();
			if (rowIndex == 0) {
				row.put(new Line(0, 0), 0);
			} else {
				// special line represents the commit at rowIndex itself
				Line commitLine = new Line(rowIndex, rowIndex);
				final Map<Line, Integer> lastRow = rows.get(rowIndex-1);
				int column = 0;
				List<Line> linesOfLastRow = new ArrayList<>(lastRow.keySet());
				for (Line lineOfLastRow: linesOfLastRow) {
					if (lineOfLastRow.parent < 0) // line is stopped due to max columns limitation
						continue;
					if (lineOfLastRow.child != lineOfLastRow.parent) {
						// line not started from last row, in this case, the line 
						// only occupies a column when it goes through current row 
						if (lineOfLastRow.parent == rowIndex) { 
							if (!row.containsKey(commitLine))
								row.put(commitLine, column++);
						} else { 
							row.put(lineOfLastRow, column++);
						}
					} else {
						for (String parentHash: commits.get(rowIndex-1).getParentHashes()) {
							Integer parent = mapOfHashToRow.get(parentHash);
							if (parent != null) {
								if (parent.intValue() == rowIndex) {
									if (!row.containsKey(commitLine))
										row.put(commitLine, column++);
								} else {
									row.put(new Line(rowIndex-1, parent), column++);
								}
							}
						}
					}
				}
				if (!row.containsKey(commitLine))
					row.put(commitLine, column++);
				if (column > maxColumns) {
					List<Line> lines = new ArrayList<>(row.keySet());
					Collections.reverse(lines);
					for (Line line: lines) {
						if (line.child == rowIndex-1) {
							row.remove(line);
							row.put(new Line(line.child, line.parent*-1), );
							line.toggle();
							column--;
							if (column == maxColumns)
								break;
						}
					}
					Preconditions.checkState(column == maxColumns);
				}
				
				List<Line> disappearedLines = new ArrayList<>();
				List<Integer> children = mapOfParentToChildren.get(rowIndex);
				if (children != null) {
					for (int child: children) {
						if (child != rowIndex-1) {
							Line line = new Line(child, rowIndex);
							if (!lastRow.containsKey(line)) {
								line.toggle();
								
								if (lastRow.containsKey(line)) {
									
								} else {
									disappearedLines.add(line);
								}
							}
						}
					}
				}
				if (!disappearedLines.isEmpty()) {
					// for every disappeared line, we need to make them appear again in last row
					// so that end part of the line can be drawn from last row to this row. 
					// Below code find column in last row to insert these appeared lines, and 
					// we want to make sure that this column can result in minimum line crossovers. 
					int commitColumn = row.get(commitLine);
					for (int i=linesOfLastRow.size()-1; i>=0; i--) {
						Line lineOfLastRow = linesOfLastRow.get(i);
						Integer lineColumn = row.get(lineOfLastRow);
						if (i == 0 || lineColumn!=null && lineColumn.intValue()<commitColumn) {
							for (int j=i+1; j<linesOfLastRow.size(); j++) 
								lastRow.remove(linesOfLastRow.get(j));
							for (Line line: disappearedLines) {
								line.appear();
								lastRow.put(line, lastRow.size());
							}
							for (int j=i+1; j<linesOfLastRow.size(); j++) 
								lastRow.put(linesOfLastRow.get(j), lastRow.size());
							break;
						}
					}
				}
			}
			rows.add(row);
		}
		
	}
	
	public List<Map<Line, Integer>> getRows() {
		return rows;
	}

	/**
	 * A line represents a child->parent relationship. For instance line(1,5)
	 * represents the line from commit at row 1 (the child ) to commit at 
	 * row 5 (the parent). In case child row index equals parent row index, 
	 * the line represents a commit. 
	 * 
	 * @author robin
	 *
	 */
	public static class Line implements Serializable {
		
		private final int child;
		
		private final int parent;
		
		public Line(int child, int parent) {
			this.child = child;
			this.parent = parent;
		}
		
		public int getChild() {
			return child;
		}

		public int getParent() {
			return parent;
		}
		
		@Override
		public boolean equals(Object other) {
			if (!(other instanceof Line))
				return false;
			if (this == other)
				return true;
			Line otherLine = (Line) other;
			return new EqualsBuilder()
					.append(child, otherLine.child)
					.append(parent, otherLine.parent)
					.isEquals();
		}

		@Override
		public int hashCode() {
			return new HashCodeBuilder(17, 37)
					.append(child)
					.append(parent)
					.toHashCode();
		}

		@Override
		public String toString() {
			return child+","+parent;
		}
		
	}
}