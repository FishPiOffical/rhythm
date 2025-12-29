package org.b3log.symphony.ai;

public sealed interface Model permits Qwen3VLPlusModel {
    String getName();
    Provider getProvider();
    boolean isTextSupported();
    boolean isImageAnalysisSupported();
}
