package com.gamecopter.wongillalib.client;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.backends.gwt.GwtApplication;
import com.badlogic.gdx.backends.gwt.GwtApplicationConfiguration;
import com.gamecopter.wongillalib.WongillalibTest;

public class HtmlLauncher extends GwtApplication {

        @Override
        public GwtApplicationConfiguration getConfig () {
                return new GwtApplicationConfiguration(640, 480);
        }

        @Override
        public ApplicationListener getApplicationListener () {
                return new WongillalibTest();
        }
}