package com.ssafy.githubble.domain.ai.component.retrieve;

public sealed interface GraphResult permits GraphResult.NodeResult, GraphResult.EdgeResult, GraphResult.GroupResult, GraphResult.CountResult {

    String kind();

    record NodeResult(
            String nodeId,
            String nodeType,
            String label,
            String description,
            double degree,
            String path,
            double score,
            boolean hub,
            String groupId
    ) implements GraphResult {

        @Override
        public String kind() { return "node"; }

        static NodeResult of(String nodeId, String nodeType, String label, String description,
                              double degree, String path, double score) {
            return new NodeResult(nodeId, nodeType, label, description, degree, path, score, false, null);
        }
    }

    record EdgeResult(
            String id,
            String fromNodeId,
            String label,
            String toNodeId,
            String description,
            double score
    ) implements GraphResult {

        @Override
        public String kind() { return "edge"; }
    }

    record GroupResult(
            String groupId,
            String label,
            String description
    ) implements GraphResult {

        @Override
        public String kind() { return "group"; }
    }

    record CountResult(
            String countType,
            long count,
            String refId,
            String label
    ) implements GraphResult {

        @Override
        public String kind() { return "count"; }

        static CountResult simple(String countType, long count) {
            return new CountResult(countType, count, null, null);
        }
    }
}
