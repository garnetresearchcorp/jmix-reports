/*
 * Copyright (c) 2008-2019 Haulmont.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.jmix.reports.libintegration;

import com.google.common.base.Strings;
import com.haulmont.cuba.core.Persistence;
import com.haulmont.cuba.core.global.AppBeans;
import com.haulmont.cuba.core.global.Metadata;
import io.jmix.core.QueryUtils;
import io.jmix.core.Resources;
import io.jmix.reports.ReportingApi;
import io.jmix.reports.entity.ParameterType;
import io.jmix.reports.entity.PredefinedTransformation;
import io.jmix.reports.entity.ReportInputParameter;
import com.haulmont.yarg.exception.ReportingException;
import com.haulmont.yarg.reporting.Reporting;
import com.haulmont.yarg.reporting.RunParams;
import com.haulmont.yarg.structure.BandData;
import com.haulmont.yarg.structure.Report;
import com.haulmont.yarg.structure.ReportParameter;
import com.haulmont.yarg.util.groovy.Scripting;
import io.jmix.ui.filter.ParametersHelper;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class JmixReporting extends Reporting {
    public static final String REPORT_FILE_NAME_KEY = "__REPORT_FILE_NAME";

    protected Scripting scripting;

    protected ReportingApi reportingApi;

    public void setScripting(Scripting scripting) {
        this.scripting = scripting;
    }

    public void setReportingApi(ReportingApi reportingApi) {
        this.reportingApi = reportingApi;
    }

    @Override
    protected String resolveOutputFileName(RunParams runParams, BandData rootBand) {
        String generatedReportFileName = (String) rootBand.getData().get(REPORT_FILE_NAME_KEY);
        if (StringUtils.isNotBlank(generatedReportFileName)) {
            return generatedReportFileName;
        } else {
            return super.resolveOutputFileName(runParams, rootBand);
        }
    }

    @Override
    protected Map<String, Object> handleParameters(Report report, Map<String, Object> params) {
        Map<String, Object> handledParams = new HashMap<String, Object>(super.handleParameters(report, params));
        for (ReportParameter reportParameter : report.getReportParameters()) {
            if (reportParameter instanceof ReportInputParameter) {
                ReportInputParameter reportInputParameter = (ReportInputParameter) reportParameter;

                String paramName = reportParameter.getAlias();
                Object paramValue = handledParams.get(paramName);

                if (BooleanUtils.isTrue(reportInputParameter.getDefaultDateIsCurrent())) {
                    handleDateTimeRelatedParameterAsNow(paramName, paramValue, reportInputParameter.getType(), handledParams);
                }

                if (reportInputParameter.getPredefinedTransformation() != null) {
                    if (paramValue != null) {
                        handledParams.put(paramName, handlePredefinedTransformation(paramValue, reportInputParameter.getPredefinedTransformation()));
                    }
                } else if (!Strings.isNullOrEmpty(reportInputParameter.getTransformationScript())) {
                    handledParams.put(paramName, handleScriptTransformation(paramValue, reportInputParameter.getTransformationScript(), handledParams));
                }
            }
        }
        return handledParams;
    }

    protected void handleDateTimeRelatedParameterAsNow(String paramName, Object paramValue, ParameterType parameterType,
                                                       Map<String, Object> handledParams) {
        if (Objects.isNull(paramValue)) {
            paramValue = reportingApi.currentDateOrTime(parameterType);
            handledParams.put(paramName, paramValue);
        }
    }

    protected Object handlePredefinedTransformation(Object value, PredefinedTransformation transformation) {
        switch (transformation) {
            case CONTAINS:
                return wrapValueForLike(QueryUtils.escapeForLike((String) value), true, true);
            case STARTS_WITH:
                return wrapValueForLike(QueryUtils.escapeForLike((String) value), false, true);
            case ENDS_WITH:
                return wrapValueForLike(QueryUtils.escapeForLike((String) value), true, false);
        }
        return value;
    }

    protected Object handleScriptTransformation(Object paramValue, String script, Map<String, Object> params) {
        Map<String, Object> scriptParams = new HashMap<>();
        scriptParams.put("params", params);
        scriptParams.put("paramValue", paramValue);
        scriptParams.put("persistence", AppBeans.get(Persistence.class));
        scriptParams.put("metadata", AppBeans.get(Metadata.class));
        script = StringUtils.trim(script);
        if (script.endsWith(".groovy")) {
            script = AppBeans.get(Resources.class).getResourceAsString(script);
        }
        return scripting.evaluateGroovy(script, scriptParams);
    }

    protected String wrapValueForLike(Object value, boolean before, boolean after) {
        return ParametersHelper.CASE_INSENSITIVE_MARKER + (before ? "%" : "") + value + (after ? "%" : "");
    }

    @Override
    protected void logException(ReportingException e) {
        //TODO ResourceCanceledException
//        if (ExceptionUtils.getRootCause(e) instanceof ResourceCanceledException) {
//            logger.info("Report is canceled by user request");
//        } else {
            super.logException(e);
//        }
    }
}