package com.github.giji34.worldgen;

import com.google.gson.Gson;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;

class Throttle {
    private final static HashMap<String, Double> kMaxCPUCreditBalance = new HashMap<>();

    private final String instanceId;
    private final String instanceType;
    private long lastCPUCreditBalanceQueryTime = 0;
    private final static long kCPUCreditaBalanceMinimumQueryIntervalMilliSeconds = 5 * 60 * 1000;
    private double cpuCreditBalance = 0;
    private final double maxCPUCreditBalance;

    static {
        kMaxCPUCreditBalance.put("t2.nano", 72.0);
        kMaxCPUCreditBalance.put("t2.micro", 144.0);
        kMaxCPUCreditBalance.put("t2.small", 288.0);
        kMaxCPUCreditBalance.put("t2.medium", 576.0);
        kMaxCPUCreditBalance.put("t2.large", 864.0);
        kMaxCPUCreditBalance.put("t2.xlarge", 1296.0);
        kMaxCPUCreditBalance.put("t2.2xlarge", 1958.4);
    }

    public Throttle() {
        instanceId = getEC2InstanceId();
        instanceType = getEC2InstanceType();
        cpuCreditBalance = queryCurrentCPUCreditBalance();
        lastCPUCreditBalanceQueryTime = System.currentTimeMillis();
        double credit = 0;
        if (instanceType != null) {
            Double d = kMaxCPUCreditBalance.get(instanceType);
            if (d != null) {
                 credit = d;
            }
        }
        maxCPUCreditBalance = credit;
    }

    public boolean isThrottleOn() {
        if (instanceId == null) {
            return true;
        }
        double credit = getCPUCreditBalance();
        return credit > maxCPUCreditBalance * 0.2;
    }

    public double getCPUCreditBalance() {
        if (instanceType == null || instanceId == null) {
            return 0;
        }
        long now = System.currentTimeMillis();
        if (now >= lastCPUCreditBalanceQueryTime + kCPUCreditaBalanceMinimumQueryIntervalMilliSeconds) {
            cpuCreditBalance = queryCurrentCPUCreditBalance();
            lastCPUCreditBalanceQueryTime = now;
        }
        return cpuCreditBalance;
    }

    public double getMaxCPUCreditBalance() {
        return maxCPUCreditBalance;
    }

    @Nullable
    private static String getEC2InstanceId() {
        return getCommandResult(new String[]{"curl", "-s", "http://169.254.169.254/latest/meta-data/instance-id"});
    }

    @Nullable
    private static String getEC2InstanceType() {
        return getCommandResult(new String[]{"curl", "-s", "http://169.254.169.254/latest/meta-data/instance-type"});
    }

    private double queryCurrentCPUCreditBalance() {
        String instanceType = this.instanceType;
        String instanceId = this.instanceId;
        if (instanceId == null || instanceType == null) {
            return 0;
        }

        OffsetDateTime startTime = OffsetDateTime.now(ZoneId.of("UTC")).minusMinutes(10);
        OffsetDateTime endTime = OffsetDateTime.now(ZoneId.of("UTC"));
        String balanceStr = getCommandResult(new String[]{
            "aws",
            "cloudwatch",
            "get-metric-statistics",
            "--namespace",
            "AWS/EC2",
            "--metric-name",
            "CPUCreditBalance",
            "--start-time",
            startTime.toString(),
            "--end-time",
            endTime.toString(),
            "--period",
            "300",
            "--statistics",
            "Maximum",
            "--dimensions",
            "Name=InstanceId,Value=" + instanceId
        });
        Gson gson = new Gson();
        Metrics metrics = gson.fromJson(balanceStr, Metrics.class);
        double cpuCreditBalance = 0;
        metrics.Datapoints.sort((Datapoint a, Datapoint b) -> {
            OffsetDateTime aTime = OffsetDateTime.parse(a.Timestamp);
            OffsetDateTime bTime = OffsetDateTime.parse(b.Timestamp);
            return (int)(aTime.toEpochSecond() - bTime.toEpochSecond());
        });
        for (Datapoint d : metrics.Datapoints) {
            cpuCreditBalance = d.Maximum;
        }
        return cpuCreditBalance;
    }

    @Nullable
    private static String getCommandResult(String[] args) {
        try {
            Process process = new ProcessBuilder(args).start();
            {
                InputStream is = process.getErrorStream();
                InputStreamReader isr = new InputStreamReader(is);
                BufferedReader br = new BufferedReader(isr);
                String line;
                String result = null;
                while ((line = br.readLine()) != null) {
                    if (result == null) {
                        result = line;
                    } else {
                        result += "\n" + line;
                    }
                }
                if (result != null) {
                    System.err.println(result);
                }
            }
            {
                InputStream is = process.getInputStream();
                InputStreamReader isr = new InputStreamReader(is);
                BufferedReader br = new BufferedReader(isr);
                String line;
                String result = null;
                while ((line = br.readLine()) != null) {
                    if (result == null) {
                        result = line;
                    } else {
                        result += "\n" + line;
                    }
                }
                return result;
            }
        } catch (Exception e) {
            return null;
        }
    }
}

class Datapoint {
    String Timestamp;
    double Maximum;
}

class Metrics {
    public List<Datapoint> Datapoints;
    public String Label;
}
