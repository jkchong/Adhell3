package com.fusionjack.adhell3.fragments;

import android.app.Activity;
import android.arch.lifecycle.LifecycleFragment;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import com.fusionjack.adhell3.App;
import com.fusionjack.adhell3.R;
import com.fusionjack.adhell3.adapter.ReportBlockedUrlAdapter;
import com.fusionjack.adhell3.db.AppDatabase;
import com.fusionjack.adhell3.db.entity.ReportBlockedUrl;
import com.sec.enterprise.firewall.DomainFilterReport;
import com.sec.enterprise.firewall.Firewall;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import javax.inject.Inject;


public class AdhellReportsFragment extends LifecycleFragment {
    private AppCompatActivity parentActivity;
    private AppDatabase appDatabase;

    @Nullable
    @Inject
    Firewall firewall;

    public AdhellReportsFragment() {
        App.get().getAppComponent().inject(this);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        parentActivity = (AppCompatActivity) getActivity();
        appDatabase = AppDatabase.getAppDatabase(getContext());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        parentActivity.setTitle("Reports");
        if (parentActivity.getSupportActionBar() != null) {
            parentActivity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            parentActivity.getSupportActionBar().setHomeButtonEnabled(true);
        }

        View view = inflater.inflate(R.layout.fragment_adhell_reports, container, false);
        SwipeRefreshLayout swipeContainer = view.findViewById(R.id.swipeContainer);
        swipeContainer.setOnRefreshListener(() -> {
            new RefreshAsynckTask(getContext(), firewall, appDatabase).execute();
        });

        new RefreshAsynckTask(getContext(), firewall, appDatabase).execute();

        return view;
    }

    private static class RefreshAsynckTask extends AsyncTask<Void, Void, List<ReportBlockedUrl>> {
        private WeakReference<Context> contextReference;
        private Firewall firewall;
        private AppDatabase appDatabase;

        RefreshAsynckTask(Context context, Firewall firewall, AppDatabase appDatabase) {
            this.contextReference = new WeakReference<>(context);
            this.firewall = firewall;
            this.appDatabase = appDatabase;
        }

        @Override
        protected List<ReportBlockedUrl> doInBackground(Void... voids) {
            List<ReportBlockedUrl> reportBlockedUrls = new ArrayList<>();
            List<DomainFilterReport> reports = firewall.getDomainFilterReport(null);
            if (reports == null) {
                return reportBlockedUrls;
            }

            long yesterday = yesterday();
            appDatabase.reportBlockedUrlDao().deleteBefore(yesterday);

            ReportBlockedUrl lastBlockedUrl = appDatabase.reportBlockedUrlDao().getLastBlockedDomain();
            long lastBlockedTimestamp = 0;
            if (lastBlockedUrl != null) {
                lastBlockedTimestamp = lastBlockedUrl.blockDate / 1000;
            }

            for (DomainFilterReport b : reports) {
                if (b.getTimeStamp() > lastBlockedTimestamp) {
                    ReportBlockedUrl reportBlockedUrl =
                            new ReportBlockedUrl(b.getDomainUrl(), b.getPackageName(), b.getTimeStamp() * 1000);
                    reportBlockedUrls.add(reportBlockedUrl);
                }
            }
            appDatabase.reportBlockedUrlDao().insertAll(reportBlockedUrls);

            return appDatabase.reportBlockedUrlDao().getReportBlockUrlBetween(yesterday(), System.currentTimeMillis());
        }

        @Override
        protected void onPostExecute(List<ReportBlockedUrl> reportBlockedUrls) {
            Context context = contextReference.get();
            if (context != null) {
                TextView lastDayInfoTextView = ((Activity) context).findViewById(R.id.lastDayInfoTextView);
                ListView blockedDomainsListView = ((Activity) context).findViewById(R.id.blockedDomainsListView);
                SwipeRefreshLayout swipeContainer = ((Activity) context).findViewById(R.id.swipeContainer);

                ReportBlockedUrlAdapter reportBlockedUrlAdapter = new ReportBlockedUrlAdapter(context, reportBlockedUrls);
                blockedDomainsListView.setAdapter(reportBlockedUrlAdapter);
                lastDayInfoTextView.setText(String.format("%s%s",
                        context.getString(R.string.last_day_blocked), String.valueOf(reportBlockedUrls.size())));
                reportBlockedUrlAdapter.notifyDataSetChanged();
                swipeContainer.setRefreshing(false);
            }
        }

        private long yesterday() {
            final Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DATE, -1);
            return cal.getTimeInMillis();
        }
    }
}
