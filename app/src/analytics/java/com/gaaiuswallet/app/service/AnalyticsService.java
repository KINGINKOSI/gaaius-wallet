package com.gaaiuswallet.app.service;

import android.content.Context;
import android.os.Bundle;

import com.gaaiuswallet.app.BuildConfig;
import com.gaaiuswallet.app.analytics.Analytics;
import com.gaaiuswallet.app.entity.AnalyticsProperties;
import com.gaaiuswallet.app.entity.ServiceErrorException;
import com.gaaiuswallet.app.repository.KeyProviderFactory;
import com.gaaiuswallet.app.repository.PreferenceRepositoryType;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.messaging.FirebaseMessaging;
import com.mixpanel.android.mpmetrics.MixpanelAPI;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

import timber.log.Timber;

public class AnalyticsService<T> implements AnalyticsServiceType<T>
{
    private final MixpanelAPI mixpanelAPI;
    private final FirebaseAnalytics firebaseAnalytics;
    private final PreferenceRepositoryType preferenceRepository;

    public AnalyticsService(Context context, PreferenceRepositoryType preferenceRepository)
    {
        this.preferenceRepository = preferenceRepository;
        mixpanelAPI = MixpanelAPI.getInstance(context, KeyProviderFactory.get().getAnalyticsKey(), false);
        firebaseAnalytics = FirebaseAnalytics.getInstance(context);
    }

    public static Bundle jsonToBundle(JSONObject jsonObject) throws JSONException
    {
        Bundle bundle = new Bundle();
        Iterator<String> it = jsonObject.keys();
        while (it.hasNext())
        {
            String key = it.next();
            String value = jsonObject.getString(key);
            bundle.putString(key, value);
        }
        return bundle;
    }

    @Override
    public void increment(String property)
    {
        if (preferenceRepository.isAnalyticsEnabled())
        {
            mixpanelAPI.getPeople().increment(property, 1);
        }
    }

    @Override
    public void track(String eventName)
    {
        if (preferenceRepository.isAnalyticsEnabled())
        {
            //firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, eventName);

            mixpanelAPI.track(eventName);
        }
    }

    @Override
    public void track(String eventName, T event)
    {
        if (preferenceRepository.isAnalyticsEnabled())
        {
            AnalyticsProperties analyticsProperties = (AnalyticsProperties) event;
            trackFirebase(analyticsProperties, eventName);
            trackMixpanel(analyticsProperties, eventName);
        }
    }

    private void trackFirebase(AnalyticsProperties analyticsProperties, String eventName)
    {
        if (preferenceRepository.isAnalyticsEnabled())
        {
            Bundle props;
            try
            {
                props = jsonToBundle(analyticsProperties.get());
                props.putString(Analytics.UserProperties.APPLICATION_ID.getValue(), BuildConfig.APPLICATION_ID);
                firebaseAnalytics.logEvent(eventName, props);
            }
            catch (JSONException e)
            {
                Timber.e(e);
            }
        }
    }

    private void trackMixpanel(AnalyticsProperties analyticsProperties, String eventName)
    {
        if (preferenceRepository.isAnalyticsEnabled())
        {
            mixpanelAPI.track(eventName, analyticsProperties.get());
        }
    }

    @Override
    public void identify(String uuid)
    {
        if (preferenceRepository.isAnalyticsEnabled())
        {
            firebaseAnalytics.setUserId(uuid);
            mixpanelAPI.identify(uuid);
            mixpanelAPI.getPeople().identify(uuid);
            mixpanelAPI.getPeople().set(Analytics.UserProperties.APPLICATION_ID.getValue(), BuildConfig.APPLICATION_ID);

            FirebaseMessaging.getInstance().getToken()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful())
                        {
                            String token = task.getResult();
                            mixpanelAPI.getPeople().setPushRegistrationId(token);
                        }
                    });
        }
    }

    @Override
    public void flush()
    {
        //Nothing like flush in firebase
        mixpanelAPI.flush();
    }

    @Override
    public void recordException(ServiceErrorException e)
    {
        if (preferenceRepository.isCrashReportingEnabled())
        {
            FirebaseCrashlytics.getInstance().recordException(e);
        }
    }
}