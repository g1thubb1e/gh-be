package com.ssafy.githubble.global.etc;

import com.ssafy.githubble.domain.github.domain.enums.GenerationJobEvent;
import com.ssafy.githubble.domain.github.domain.enums.GenerationJobStep;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class JobManager {
    private final Map<String, Set<GenerationJobStep>> completedStepsMap = new ConcurrentHashMap<>();
    private final Map<String, Boolean> generationStatusMap = new ConcurrentHashMap<>();
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    private final Set<GenerationJobStep> todoList = Set.of(
            GenerationJobStep.DIAGRAM,
            GenerationJobStep.PARSING,
            GenerationJobStep.EXPLANATION,
            GenerationJobStep.RAG,
            GenerationJobStep.TECH_STACK
    );

    private void saveJobStatus(String jobId, GenerationJobStep completedJob){
        completedStepsMap.get(jobId).add(completedJob);
    }

    public boolean startGeneration(String jobId){
        if(!generationStatusMap.containsKey(jobId)) {
            generationStatusMap.put(jobId, false);
            return true;
        }
        return generationStatusMap.get(jobId);
    }

    // 새로운 작업을 생성, job 기준은 repo 정보
    public void register(String jobId, SseEmitter sseEmitter) {
        emitters.computeIfAbsent(jobId, key -> new CopyOnWriteArrayList<>())
                .add(sseEmitter);

        // 이전 작업 스냅샷 전송
        if(!completedStepsMap.containsKey(jobId))completedStepsMap.put(jobId, new HashSet<>());
        Set<GenerationJobStep> completedSteps = completedStepsMap.get(jobId);
        try {
            sseEmitter.send(SseEmitter.event()
                    .name(String.valueOf(GenerationJobEvent.SNAPSHOT))
                    .data(completedSteps.toArray()));
        } catch (IOException e) {
            remove(jobId, sseEmitter);
        }
    }

    public void remove(String jobId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(jobId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(jobId);
            }
        }
    }

    public void jobCompleted(String jobId, GenerationJobEvent event, GenerationJobStep step){
        saveJobStatus(jobId, step);
        send(jobId, event, step.toString());
    }

    public void send(String jobId, GenerationJobEvent event, String message) {
        sendData(jobId, event, Map.of(
                "message", message
        ));
    }

    public void sendData(String jobId, GenerationJobEvent event, Object data) {
        List<SseEmitter> list = emitters.get(jobId);
        if (list == null) return;

        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event()
                        .name(String.valueOf(event))
                        .data(data));
            } catch (IOException e) {
                remove(jobId, emitter);
            }
        }
    }

    public boolean completeCheck(String jobId){
        Set<GenerationJobStep> doneJobs = completedStepsMap.get(jobId);
        for(GenerationJobStep step: todoList){
            if(!doneJobs.contains(step)) return false;
        }
        return true;
    }

    public boolean isRunning(String jobId) {
        return generationStatusMap.containsKey(jobId);
    }

    public void complete(String jobId) {
        List<SseEmitter> list = emitters.remove(jobId);
        completedStepsMap.remove(jobId);
        generationStatusMap.remove(jobId);
        if (list == null) return;

        for (SseEmitter emitter : list) {
            emitter.complete();
        }
    }
}
