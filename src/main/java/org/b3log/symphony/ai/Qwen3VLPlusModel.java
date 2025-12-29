package org.b3log.symphony.ai;

public final class Qwen3VLPlusModel implements Model {
    @Override
    public String getName() {
        return "qwen3-vl-plus";
    }

    @Override
    public Provider getProvider() {
        return new OpenAIProvider();
    }

    @Override
    public boolean isTextSupported() {
        return true;
    }

    @Override
    public boolean isImageAnalysisSupported() {
        return true;
    }
}
