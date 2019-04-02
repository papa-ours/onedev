package io.onedev.server.model.support.jobexecutor;

import java.util.List;

import io.onedev.server.web.editable.annotation.Editable;

@Editable(order=100)
public class LocalhostExecutor implements JobExecutor {

	private static final long serialVersionUID = 1L;

	@Override
	public String run(String image, List<String> commands) {
		return null;
	}
	
	@Override
	public boolean isRunning(String runningInstance) {
		return false;
	}

	@Override
	public void stop(String runningInstance) {
	}
	
}