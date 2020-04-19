package com.zjmzxfzhl.modules.sys.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zjmzxfzhl.common.Constants;
import com.zjmzxfzhl.common.base.BaseServiceImpl;
import com.zjmzxfzhl.common.exception.SysException;
import com.zjmzxfzhl.common.permission.FilterOperate;
import com.zjmzxfzhl.common.query.QueryWrapperGenerator;
import com.zjmzxfzhl.common.util.CommonUtil;
import com.zjmzxfzhl.common.util.RedisUtil;
import com.zjmzxfzhl.modules.sys.entity.SysCodeInfo;
import com.zjmzxfzhl.modules.sys.mapper.SysCodeInfoMapper;
import com.zjmzxfzhl.modules.sys.service.SysCodeInfoService;

/**
 * 代码信息ServiceImpl
 * 
 * @author 庄金明
 */
@Service
public class SysCodeInfoServiceImpl extends BaseServiceImpl<SysCodeInfoMapper, SysCodeInfo>
        implements SysCodeInfoService {

    @Autowired
    private RedisUtil redisUtil;

    @Override
    public IPage<SysCodeInfo> list(IPage<SysCodeInfo> page, SysCodeInfo sysCodeInfo) {
        return page.setRecords(baseMapper.list(page, sysCodeInfo));
    }

    /**
     * 加载数据字典进redis
     * 
     * @param codeTypeIds
     *            数据字典数组，以逗号隔开， 若传空则加载所有数据字典
     */
    @Override
    public void loadSysCodeInfoToRedis(String codeTypeIds) {
        if (CommonUtil.isNotEmptyAfterTrim(codeTypeIds)) {
            String[] codeTypeIdsArr = codeTypeIds.split(",");
            for (String codeTypeId : codeTypeIdsArr) {
                // 先清除
                redisUtil.del(Constants.PREFIX_SYS_CODE_TYPE + codeTypeId);
            }
        } else {
            // 先清除
            redisUtil.delPattern(Constants.PREFIX_SYS_CODE_TYPE + "*");
        }
        Map<String, List<SysCodeInfo>> codeInfoMap = getSysCodeInfosFromDb(codeTypeIds);
        for (String codeInfoMapKey : codeInfoMap.keySet()) {
            redisUtil.set(Constants.PREFIX_SYS_CODE_TYPE + codeInfoMapKey, codeInfoMap.get(codeInfoMapKey));
        }
    }

    /**
     * 从数据库查询数据字典
     * 
     * @param codeTypeIds
     *            数据字典数组，以逗号隔开， 若传空则加载所有数据字典
     * @return
     */
    @Override
    public Map<String, List<SysCodeInfo>> getSysCodeInfosFromDb(String codeTypeIds) {
        Map<String, List<SysCodeInfo>> codeInfoMap = new HashMap<String, List<SysCodeInfo>>(16);
        QueryWrapper<SysCodeInfo> queryWrapper = new QueryWrapper<>();
        if (CommonUtil.isNotEmptyAfterTrim(codeTypeIds)) {
            QueryWrapperGenerator.addEasyQuery(queryWrapper, "codeTypeId", FilterOperate.IN, codeTypeIds);
        }
        queryWrapper.orderByAsc("CODE_TYPE_ID", "SORT_NO");
        List<SysCodeInfo> codeInfoList = this.list(queryWrapper);
        for (SysCodeInfo sysCodeInfo : codeInfoList) {
            List<SysCodeInfo> subList = null;
            if (!codeInfoMap.containsKey(sysCodeInfo.getCodeTypeId())) {
                subList = new ArrayList<SysCodeInfo>();
                SysCodeInfo emptySysCodeInfo = new SysCodeInfo();
                emptySysCodeInfo.setCodeInfoId("");
                emptySysCodeInfo.setCodeTypeId(sysCodeInfo.getCodeTypeId());
                emptySysCodeInfo.setContent("");
                emptySysCodeInfo.setValue("");
                emptySysCodeInfo.setSortNo(-999);
                subList.add(emptySysCodeInfo);
            } else {
                subList = codeInfoMap.get(sysCodeInfo.getCodeTypeId());
            }
            subList.add(sysCodeInfo);
            codeInfoMap.put(sysCodeInfo.getCodeTypeId(), subList);
        }
        return codeInfoMap;
    }

    /**
     * 查询数据字典
     * 
     * @param codeTypeIds
     *            数据字典数组，以逗号隔开， 若传空则加载所有数据字典
     * @return
     */
    @Override
    @SuppressWarnings("unchecked")
    public Map<String, List<SysCodeInfo>> getSysCodeInfosFromRedis(String codeTypeIds) {
        Set<String> keys = null;
        if (CommonUtil.isNotEmptyAfterTrim(codeTypeIds)) {
            keys = new HashSet<>();
            String[] codeTypeIdsArr = codeTypeIds.split(",");
            for (String codeTypeId : codeTypeIdsArr) {
                keys.add(Constants.PREFIX_SYS_CODE_TYPE + codeTypeId);
            }
        } else {
            keys = redisUtil.keysPattern(Constants.PREFIX_SYS_CODE_TYPE + "*");
        }
        Map<String, List<SysCodeInfo>> codeInfoMap = null;
        if (keys != null && keys.size() > 0) {
            codeInfoMap = new HashMap<String, List<SysCodeInfo>>(16);
            for (String key : keys) {
                codeInfoMap.put(key.replace(Constants.PREFIX_SYS_CODE_TYPE, ""),
                        (List<SysCodeInfo>) redisUtil.get(key));
            }
        }
        return codeInfoMap;
    }

    /**
     * 新增代码信息，并加载进redis缓存
     * 
     * @param sysCodeInfo
     */
    @Override
    public void saveSysCodeInfo(SysCodeInfo sysCodeInfo) {
        this.save(sysCodeInfo);
        this.loadSysCodeInfoToRedis(sysCodeInfo.getCodeTypeId());
    }

    /**
     * 修改代码信息，并加载进redis缓存
     * 
     * @param sysCodeInfo
     */
    @Override
    public void updateSysCodeInfo(SysCodeInfo sysCodeInfo) {
        this.updateById(sysCodeInfo);
        this.loadSysCodeInfoToRedis(sysCodeInfo.getCodeTypeId());
    }

    /**
     * 删除代码信息，并重新加载redis缓存
     * 
     * @param ids
     */
    @Override
    public void deleteSysCodeInfo(String ids) {
        if (ids == null || ids.trim().length() == 0) {
            throw new SysException("ids can't be empty");
        }
        String[] idsArr = ids.split(",");
        if (idsArr.length > 1) {
            this.removeByIds(Arrays.asList(idsArr));
        } else {
            this.removeById(idsArr[0]);
        }
        this.loadSysCodeInfoToRedis(null);
    }
}
