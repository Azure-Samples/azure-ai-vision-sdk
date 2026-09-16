package com.azure.ai.vision.face.sample.store;

/** App-session persistence (Face credentials for result polling). */
public interface AppSessionStore {

    boolean save(String sid, AppSession session);

    AppSession get(String sid);
}
