import streamlit as st
import pandas as pd
import os

# 配置页面
st.set_page_config(
    page_title="电力交易数据分析",
    page_icon="⚡",
    layout="wide"
)

# 侧边栏标题
st.sidebar.title("电力交易数据分析")

# 选择功能模块
function_module = st.sidebar.selectbox(
    "选择功能模块",
    ["中长期台账", "现货电价跟踪", "地区现货分析", "现货均价显示"]
)

# 辅助函数：格式化DataFrame用于显示（将NaN显示为"-"）
def format_display_df(df, nan_columns):
    """将指定列的NaN值格式化为"-"用于显示，保持数据为numeric类型"""
    display_df = df.copy()
    for col in nan_columns:
        if col in display_df.columns:
            # 将NaN替换为特殊值用于显示，但保持原数据类型
            display_df[col] = display_df[col].where(pd.notna(display_df[col]), other=None)
    return display_df

# 辅助函数：为DataFrame列应用格式化样式
def style_dataframe(df, nan_columns):
    """为DataFrame应用样式，使NaN显示为"-" """
    import math
    
    def format_value(val):
        if pd.isna(val):
            return "-"
        elif isinstance(val, float) and math.isnan(val):
            return "-"
        return val
    
    styled_df = df.copy()
    for col in nan_columns:
        if col in styled_df.columns:
            styled_df[col] = styled_df[col].apply(format_value)
    return styled_df

# 读取所属公司序号映射
@st.cache_data
def load_company_order():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)
    company_file = os.path.join(project_root, '新版统计', '所属公司.xlsx')
    if os.path.exists(company_file):
        return pd.read_excel(company_file)
    return None

company_order_df = load_company_order()

# 读取中长期数据
@st.cache_data
def load_longterm_data():
    # 获取项目根目录
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)
    output_dir = os.path.join(project_root, 'output')
    files = [f for f in os.listdir(output_dir) if f.startswith('longterm_') and f.endswith('.xlsx')]
    if files:
        latest_file = sorted(files)[-1]
        return pd.read_excel(os.path.join(output_dir, latest_file))
    return None

# 读取现货数据
@st.cache_data
def load_spot_data():
    # 获取项目根目录
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)
    output_dir = os.path.join(project_root, 'output')
    
    files = [f for f in os.listdir(output_dir) if f.startswith('spot_') and f.endswith('.xlsx')]
    if files:
        latest_file = sorted(files)[-1]
        sheets = pd.read_excel(os.path.join(output_dir, latest_file), sheet_name=None)
        dfs = []
        for sheet_name, df in sheets.items():
            df['发电类型'] = sheet_name
            dfs.append(df)
        return pd.concat(dfs, ignore_index=True)
    return None

# 读取煤电装机量数据
@st.cache_data
def load_coal_capacity():
    # 获取项目根目录
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)
    capacity_file = os.path.join(project_root, 'data', '装机量.xlsx')
    if os.path.exists(capacity_file):
        return pd.read_excel(capacity_file, sheet_name='煤电')
    return None

# 现货发电电价跟踪功能
def spot_coal_price_tracking():
    st.header("现货发电电价跟踪")
    
    longterm_df = load_longterm_data()
    spot_df = load_spot_data()
    capacity_df = load_coal_capacity()
    
    if longterm_df is None:
        st.error("未找到中长期交易数据，请先运行ETL脚本生成数据！")
        return
    
    # 侧边栏筛选条件
    st.sidebar.subheader("筛选条件")
    
    # 发电类型筛选
    power_type_options = ['煤机', '风电', '光伏']
    selected_power_type = st.sidebar.selectbox(
        "发电类型",
        power_type_options,
        index=0,
        help="选择发电类型"
    )
    
    # 中长期数据的发电类型映射
    longterm_power_type_map = {
        '煤机': '煤电',
        '风电': '风电',
        '光伏': '光伏'
    }
    current_longterm_type = longterm_power_type_map[selected_power_type]
    
    # 获取合同执行周期选项（用于中长期数据筛选）
    contract_periods = sorted([p for p in longterm_df['合同执行周期'].unique() if pd.notna(p)])
    
    # 选择合同执行周期
    selected_periods = st.sidebar.multiselect(
        "合同执行周期",
        contract_periods,
        default=None,
        help="可多选，不选则包含所有周期"
    )
    
    # 获取现货数据的月份选项
    month_options = []
    if spot_df is not None:
        current_spot_df = spot_df[spot_df['发电类型'] == selected_power_type].copy()
        current_spot_df['月份'] = current_spot_df['日期'].dt.to_period('M')
        months = sorted(current_spot_df['月份'].unique())
        month_options = [f"{m.year}年{m.month}月" for m in months]
    
    # 选择月份（用于现货数据筛选）
    selected_months = st.sidebar.multiselect(
        "现货数据月份",
        month_options,
        default=None,
        help="可多选，按月筛选现货数据"
    )
    
    # ========== 计算年度交易数据 ==========
    yearly_filtered = longterm_df[
        (longterm_df['交易周期'].isin(['年度', '年度分解'])) &
        (longterm_df['发电类型'] == current_longterm_type)
    ]
    if selected_periods:
        yearly_filtered = yearly_filtered[yearly_filtered['合同执行周期'].isin(selected_periods)]
    yearly_filtered = yearly_filtered.copy()
    yearly_filtered['合同量价乘积'] = yearly_filtered['华能成交电量/亿千瓦时'] * yearly_filtered['华能成交价格元/千千瓦时']
    
    yearly_result = yearly_filtered.groupby('所属公司').agg(
        年度成交电量=('华能成交电量/亿千瓦时', 'sum'),
        年度合同量价乘积=('合同量价乘积', 'sum')
    ).reset_index()
    yearly_result['年度交易电价'] = yearly_result.apply(
        lambda row: row['年度合同量价乘积'] / row['年度成交电量'] if row['年度成交电量'] > 0 else 0,
        axis=1
    )
    
    # ========== 计算月度交易数据 ==========
    monthly_filtered = longterm_df[
        (longterm_df['交易周期'].isin(['月度', '月度分解', '多月', '多月分解'])) &
        (longterm_df['发电类型'] == current_longterm_type)
    ]
    if selected_periods:
        monthly_filtered = monthly_filtered[monthly_filtered['合同执行周期'].isin(selected_periods)]
    monthly_filtered = monthly_filtered.copy()
    monthly_filtered['合同量价乘积'] = monthly_filtered['华能成交电量/亿千瓦时'] * monthly_filtered['华能成交价格元/千千瓦时']
    
    monthly_result = monthly_filtered.groupby('所属公司').agg(
        月度成交电量=('华能成交电量/亿千瓦时', 'sum'),
        月度合同量价乘积=('合同量价乘积', 'sum')
    ).reset_index()
    monthly_result['月度交易电价'] = monthly_result.apply(
        lambda row: row['月度合同量价乘积'] / row['月度成交电量'] if row['月度成交电量'] > 0 else 0,
        axis=1
    )
    
    # ========== 计算现货数据 ==========
    spot_result = None
    if spot_df is not None:
        current_spot_df = spot_df[spot_df['发电类型'] == selected_power_type].copy()
        current_spot_df['年份'] = current_spot_df['日期'].dt.year
        current_spot_df['月份'] = current_spot_df['日期'].dt.month
        
        if selected_months:
            selected_month_values = []
            for month_str in selected_months:
                year = int(month_str.split('年')[0])
                month = int(month_str.split('年')[1].replace('月', ''))
                selected_month_values.append((year, month))
            mask = current_spot_df.apply(lambda row: (row['年份'], row['月份']) in selected_month_values, axis=1)
            current_spot_df = current_spot_df[mask]
        
        current_spot_df['中长期收入'] = current_spot_df['中长期合约电量'] * current_spot_df['中长期合约电价']
        current_spot_df['日清分收入'] = current_spot_df['上网电量'] * current_spot_df['日清分电价']
        
        spot_result = current_spot_df.groupby('公司').agg(
            上网电量=('上网电量', 'sum'),
            中长期合约电量=('中长期合约电量', 'sum'),
            中长期收入=('中长期收入', 'sum'),
            日清分收入=('日清分收入', 'sum'),
            统一结算点实时均价=('统一结算点实时均价', 'mean')
        ).reset_index()
        
        spot_result['中长期均价'] = spot_result.apply(
            lambda row: row['中长期收入'] / row['中长期合约电量'] if row['中长期合约电量'] > 0 else 0,
            axis=1
        )
        spot_result['持仓率'] = spot_result.apply(
            lambda row: row['中长期合约电量'] / row['上网电量'] if row['上网电量'] > 0 else 0,
            axis=1
        )
        spot_result['日清分均价'] = spot_result.apply(
            lambda row: row['日清分收入'] / row['上网电量'] if row['上网电量'] > 0 else 0,
            axis=1
        )
        spot_result['上网电量/亿千瓦时'] = spot_result['上网电量'] / 100000
        spot_result['中长期合约电量/亿千瓦时'] = spot_result['中长期合约电量'] / 100000
    
    # 创建四个tab（现货数据放在第一个）
    tab1, tab2, tab3, tab4 = st.tabs(["⚡ 现货数据", "📅 中长期年度数据", "📆 中长期月度数据", "📊 1+3表"])
    
    # 现货煤机数据展示（放在第一个tab）
    with tab1:
        st.subheader("现货煤机数据透视表")
        if spot_result is None:
            st.error("未找到现货交易数据，请先运行ETL脚本生成数据！")
        else:
            display_result = spot_result.copy()
            if company_order_df is not None:
                display_result = display_result.merge(company_order_df, left_on='公司', right_on='公司', how='right')
                display_result = display_result.sort_values('序号').reset_index(drop=True)
                display_result = display_result[[
                    '序号', '公司', '上网电量/亿千瓦时', '中长期合约电量/亿千瓦时',
                    '中长期均价', '持仓率', '统一结算点实时均价', '日清分均价'
                ]]
            else:
                display_result = display_result[[
                    '公司', '上网电量/亿千瓦时', '中长期合约电量/亿千瓦时',
                    '中长期均价', '持仓率', '统一结算点实时均价', '日清分均价'
                ]]
            st.dataframe(display_result, width='stretch')
            
            col1, col2 = st.columns(2)
            with col1:
                st.subheader("📊 上网电量统计")
                chart_data = display_result.dropna(subset=['上网电量/亿千瓦时'])
                st.bar_chart(chart_data.set_index('公司')['上网电量/亿千瓦时'])
            with col2:
                st.subheader("📊 持仓率分布")
                chart_data = display_result.dropna(subset=['持仓率'])
                st.bar_chart(chart_data.set_index('公司')['持仓率'])
    
    # 年度交易数据展示
    with tab2:
        st.subheader(f"年度交易数据（{current_longterm_type} - 交易周期：年度、年度分解）")
        display_result = yearly_result.copy()
        if company_order_df is not None:
            # 使用right join保留所有公司
            display_result = display_result.merge(company_order_df, left_on='所属公司', right_on='公司', how='right')
            display_result = display_result.sort_values('序号').reset_index(drop=True)
            # 如果所属公司为NaN，使用公司列的值
            display_result['所属公司'] = display_result['所属公司'].fillna(display_result['公司'])
            display_result = display_result[['序号', '所属公司', '年度成交电量', '年度交易电价']]
        else:
            display_result = display_result[['所属公司', '年度成交电量', '年度交易电价']]
        
        # 应用格式化样式（NaN显示为"-"）
        display_result = style_dataframe(display_result, ['年度成交电量', '年度交易电价'])
        st.dataframe(display_result, width='stretch')
        
        st.subheader("📊 年度成交电量统计")
        chart_data = yearly_result.copy()
        if not chart_data.empty:
            st.bar_chart(chart_data.set_index('所属公司')['年度成交电量'])
        st.subheader("📊 年度华能成交电价")
        chart_data = yearly_result.copy()
        if not chart_data.empty:
            st.bar_chart(chart_data.set_index('所属公司')['年度交易电价'])
    
    # 月度交易数据展示
    with tab3:
        st.subheader(f"月度交易数据（{current_longterm_type} - 交易周期：月度、月度分解、多月、多月分解）")
        display_result = monthly_result.copy()
        if company_order_df is not None:
            # 使用right join保留所有公司
            display_result = display_result.merge(company_order_df, left_on='所属公司', right_on='公司', how='right')
            display_result = display_result.sort_values('序号').reset_index(drop=True)
            # 如果所属公司为NaN，使用公司列的值
            display_result['所属公司'] = display_result['所属公司'].fillna(display_result['公司'])
            display_result = display_result[['序号', '所属公司', '月度成交电量', '月度交易电价']]
        else:
            display_result = display_result[['所属公司', '月度成交电量', '月度交易电价']]
        
        # 应用格式化样式（NaN显示为"-"）
        display_result = style_dataframe(display_result, ['月度成交电量', '月度交易电价'])
        st.dataframe(display_result, width='stretch')
        
        st.subheader("📊 月度成交电量统计")
        chart_data = monthly_result.copy()
        if not chart_data.empty:
            st.bar_chart(chart_data.set_index('所属公司')['月度成交电量'])
        st.subheader("📊 月度华能成交电价")
        chart_data = monthly_result.copy()
        if not chart_data.empty:
            st.bar_chart(chart_data.set_index('所属公司')['月度交易电价'])
    
    # 现货煤机数据展示（已移至tab1）
    
    # ========== 最终汇总表 ==========
    with tab4:
        st.subheader("📊 1+3表")
        
        # 从公司列表开始，确保所有公司都显示
        final_df = company_order_df.copy() if company_order_df is not None else pd.DataFrame({'公司': []})
        
        # 合并年度数据
        final_df = final_df.merge(
            yearly_result[['所属公司', '年度成交电量', '年度交易电价']],
            left_on='公司',
            right_on='所属公司',
            how='left'
        )
        
        # 合并月度数据
        final_df = final_df.merge(
            monthly_result[['所属公司', '月度交易电价']],
            left_on='公司',
            right_on='所属公司',
            how='left'
        )
        
        # 合并现货数据
        if spot_result is not None:
            final_df = final_df.merge(
                spot_result[['公司', '上网电量', '上网电量/亿千瓦时', '持仓率', '中长期均价', '统一结算点实时均价', '日清分均价']],
                on='公司',
                how='left'
            )
        
        # 合并装机量数据（仅煤电）
        if capacity_df is not None and selected_power_type == '煤机':
            final_df = final_df.merge(capacity_df, on='公司', how='left')
        
        # 计算年度合约持仓占比 = 年度成交电量 / 上网电量（上网电量原始值）
        final_df['年度合约持仓占比'] = final_df.apply(
            lambda row: row['年度成交电量'] / (row['上网电量'] / 100000) if pd.notna(row['年度成交电量']) and pd.notna(row['上网电量']) and row['上网电量'] > 0 else 0,
            axis=1
        )
        
        # 年度+月度合约持仓占比 = 持仓率（来自现货表）
        final_df['年度+月度合约持仓占比'] = final_df['持仓率']
        
        # 交易比价（较年度长协提价）
        def calculate_price_ratio(row):
            if pd.notna(row['年度交易电价']) and row['年度交易电价'] != 0:
                return row['日清分均价'] - row['年度交易电价']
            elif pd.notna(row['月度交易电价']) and row['月度交易电价'] != 0:
                return row['日清分均价'] - row['月度交易电价']
            return 0
        
        final_df['交易比价（较年度长协提价）'] = final_df.apply(calculate_price_ratio, axis=1)
        
        # 单位千瓦收入 = 日清分价格 * 上网电量 * 10 / 装机量 / 100000（仅煤电）
        if selected_power_type == '煤机':
            final_df['单位千瓦收入'] = final_df.apply(
                lambda row: row['日清分均价'] * row['上网电量'] * 10 / row['装机量'] / 100000 
                if pd.notna(row['日清分均价']) and pd.notna(row['上网电量']) and pd.notna(row['装机量']) and row['装机量'] > 0 else 0,
                axis=1
            )
        
        # 选择最终列并排序
        final_df = final_df.sort_values('序号').reset_index(drop=True)
        
        # 根据发电类型选择显示的列
        if selected_power_type == '煤机':
            final_columns = [
                '公司', 
                '年度合约持仓占比', 
                '年度+月度合约持仓占比', 
                '年度交易电价', 
                '月度交易电价', 
                '中长期均价', 
                '统一结算点实时均价', 
                '日清分均价', 
                '交易比价（较年度长协提价）', 
                '单位千瓦收入'
            ]
        else:
            final_columns = [
                '公司', 
                '年度合约持仓占比', 
                '年度+月度合约持仓占比', 
                '年度交易电价', 
                '月度交易电价', 
                '中长期均价', 
                '统一结算点实时均价', 
                '日清分均价', 
                '交易比价（较年度长协提价）'
            ]
        
        final_df = final_df[final_columns]
        
        # 显示数据
        st.dataframe(final_df, width='stretch')
        
        # 可视化
        col1, col2 = st.columns(2)
        with col1:
            st.subheader("📊 年度合约持仓占比")
            chart_data = final_df.dropna(subset=['年度合约持仓占比'])
            st.bar_chart(chart_data.set_index('公司')['年度合约持仓占比'])
        with col2:
            st.subheader("📊 交易比价")
            chart_data = final_df.dropna(subset=['交易比价（较年度长协提价）'])
            st.bar_chart(chart_data.set_index('公司')['交易比价（较年度长协提价）'])

# 数据概览功能
def data_overview():
    # 选择数据源
    data_source = st.sidebar.selectbox(
        "选择数据源",
        ["中长期交易数据", "现货交易数据"]
    )
    
    if data_source == "中长期交易数据":
        df = load_longterm_data()
    else:
        df = load_spot_data()
    
    if df is None:
        st.error(f"未找到{data_source}，请先运行ETL脚本生成数据！")
        st.stop()
    
    # 显示数据概览
    st.header(f"{data_source}概览")
    st.write(f"总记录数: **{len(df):,}** 条")
    st.write(f"数据列数: **{len(df.columns)}** 列")
    
    # 筛选控件
    st.sidebar.subheader("筛选条件")
    
    if data_source == "中长期交易数据":
        # 中长期数据筛选
        company_filter = st.sidebar.multiselect(
            "选择所属公司",
            df['所属公司'].unique(),
            default=None
        )
        
        type_filter = st.sidebar.multiselect(
            "选择发电类型",
            df['发电类型'].unique(),
            default=None
        )
        
        region_filter = st.sidebar.multiselect(
            "选择区域",
            df['区域'].unique(),
            default=None
        )
        
        # 应用筛选
        filtered_df = df.copy()
        if company_filter:
            filtered_df = filtered_df[filtered_df['所属公司'].isin(company_filter)]
        if type_filter:
            filtered_df = filtered_df[filtered_df['发电类型'].isin(type_filter)]
        if region_filter:
            filtered_df = filtered_df[filtered_df['区域'].isin(region_filter)]
    
    else:
        # 现货数据筛选
        company_filter = st.sidebar.multiselect(
            "选择公司",
            df['公司'].unique(),
            default=None
        )
        
        type_filter = st.sidebar.multiselect(
            "选择发电类型",
            df['发电类型'].unique(),
            default=None
        )
        
        # 应用筛选
        filtered_df = df.copy()
        if company_filter:
            filtered_df = filtered_df[filtered_df['公司'].isin(company_filter)]
        if type_filter:
            filtered_df = filtered_df[filtered_df['发电类型'].isin(type_filter)]
    
    # 显示筛选后的数据
    st.subheader(f"筛选后数据 ({len(filtered_df):,} 条)")
    st.dataframe(filtered_df.head(100))
    
    # 统计分析
    st.header("📊 统计分析")
    
    if data_source == "中长期交易数据":
        # 中长期数据分析
        col1, col2 = st.columns(2)
        
        with col1:
            st.subheader("按发电类型统计")
            type_stats = filtered_df.groupby('发电类型')['华能成交电量/亿千瓦时'].sum().reset_index()
            st.bar_chart(type_stats.set_index('发电类型'))
        
        with col2:
            st.subheader("按区域统计")
            region_stats = filtered_df.groupby('区域')['华能成交电量/亿千瓦时'].sum().reset_index()
            st.bar_chart(region_stats.set_index('区域'))
        
        # 成交价格分析
        st.subheader("成交价格分布")
        price_stats = filtered_df.groupby('所属公司')['华能成交价格元/千千瓦时'].mean().reset_index()
        st.bar_chart(price_stats.set_index('所属公司'))
    
    else:
        # 现货数据分析
        col1, col2 = st.columns(2)
        
        with col1:
            st.subheader("按发电类型统计")
            type_stats = filtered_df.groupby('发电类型')['日清分电价'].mean().reset_index()
            st.bar_chart(type_stats.set_index('发电类型'))
        
        with col2:
            st.subheader("按公司统计")
            company_stats = filtered_df.groupby('公司')['日清分电价'].mean().reset_index().head(20)
            st.bar_chart(company_stats.set_index('公司'))
        
        # 上网电量分析
        st.subheader("上网电量统计")
        power_stats = filtered_df.groupby('发电类型')['上网电量'].sum().reset_index()
        st.bar_chart(power_stats.set_index('发电类型'))
    
    # 数据下载
    st.header("📥 数据下载")
    csv_data = filtered_df.to_csv(index=False, encoding='utf-8-sig')
    st.download_button(
        label="下载筛选后数据",
        data=csv_data,
        file_name=f"{data_source}_筛选数据.csv",
        mime="text/csv"
    )
    
    # 页脚
    st.sidebar.markdown("---")
    st.sidebar.info("数据来源: output目录下的Excel文件")
    st.sidebar.info("运行 `python scripts/longterm_etl.py` 或 `python scripts/spot_etl.py` 生成数据")

# 中长期台账功能
def longterm_ledger():
    st.header("中长期台账")
    
    longterm_df = load_longterm_data()
    
    if longterm_df is None:
        st.error("未找到中长期交易数据，请先运行ETL脚本生成数据！")
        return
    
    # 获取筛选条件的选项
    transaction_types = sorted([t for t in longterm_df['交易类型'].unique() if pd.notna(t)])
    power_types = sorted([t for t in longterm_df['发电类型'].unique() if pd.notna(t)])
    transaction_periods = sorted([p for p in longterm_df['交易周期'].unique() if pd.notna(p)])
    contract_periods = sorted([p for p in longterm_df['合同执行周期'].unique() if pd.notna(p)])
    green_power_options = sorted([g for g in longterm_df['是否为绿电交易'].unique() if pd.notna(g)])
    
    # 筛选控件
    st.sidebar.subheader("筛选条件")
    
    # 选择哪个筛选条件作为多选项
    multi_select_dimension = st.sidebar.selectbox(
        "选择多选项维度",
        ["交易类型", "发电类型", "交易周期", "合同执行周期", "是否绿电"],
        index=1,  # 默认选择发电类型
        help="选择哪个筛选维度支持多选对比"
    )
    
    # 初始化选中的值
    selected_transaction_types = []
    selected_power_types = []
    selected_transaction_periods = []
    selected_contract_periods = []
    selected_green_power = []
    
    # 根据选择的维度显示对应的多选模式
    if multi_select_dimension == "交易类型":
        # 交易类型作为多选项
        st.sidebar.info("未选择交易类型时，默认使用所有交易类型")
        
        # 使用session state保存已选择的交易类型列表
        if 'selected_transaction_types_list' not in st.session_state:
            st.session_state.selected_transaction_types_list = []
        
        # 添加交易类型下拉框
        transaction_type_to_add = st.sidebar.selectbox(
            "添加交易类型",
            [t for t in transaction_types if t not in st.session_state.selected_transaction_types_list],
            index=0 if transaction_types else 0,
            disabled=len(st.session_state.selected_transaction_types_list) >= len(transaction_types)
        )
        
        # 添加按钮
        add_button_clicked = st.sidebar.button("➕ 添加交易类型", key="add_transaction_type", 
                         disabled=len(st.session_state.selected_transaction_types_list) >= len(transaction_types),
                         use_container_width=True)
        
        # 处理添加按钮点击
        if add_button_clicked:
            if transaction_type_to_add and transaction_type_to_add not in st.session_state.selected_transaction_types_list:
                st.session_state.selected_transaction_types_list.append(transaction_type_to_add)
        
        # 显示已选择的交易类型和删除按钮
        if st.session_state.selected_transaction_types_list:
            st.sidebar.write("已选择的交易类型：")
            for idx, tt in enumerate(st.session_state.selected_transaction_types_list):
                cols = st.sidebar.columns([3, 1])
                cols[0].write(f"{idx+1}. {tt}")
                if cols[1].button("×", key=f"remove_transaction_{idx}"):
                    st.session_state.selected_transaction_types_list.pop(idx)
                    st.rerun()
        
        selected_transaction_types = st.session_state.selected_transaction_types_list
        
        # 其他筛选项正常显示
        selected_power_types = st.sidebar.multiselect("发电类型", power_types, default=None)
        selected_transaction_periods = st.sidebar.multiselect("交易周期", transaction_periods, default=None)
        selected_contract_periods = st.sidebar.multiselect("合同执行周期", contract_periods, default=None)
        selected_green_power = st.sidebar.multiselect("是否绿电", green_power_options, default=None)
    
    elif multi_select_dimension == "发电类型":
        # 发电类型作为多选项（原有逻辑）
        st.sidebar.info("未选择发电类型时，默认使用所有发电类型")
        
        # 使用session state保存已选择的发电类型列表
        if 'selected_power_types_list' not in st.session_state:
            st.session_state.selected_power_types_list = []
        
        # 添加发电类型下拉框
        power_type_to_add = st.sidebar.selectbox(
            "添加发电类型",
            [p for p in power_types if p not in st.session_state.selected_power_types_list],
            index=0 if power_types else 0,
            disabled=len(st.session_state.selected_power_types_list) >= len(power_types)
        )
        
        # 添加按钮
        add_button_clicked = st.sidebar.button("➕ 添加发电类型", key="add_power_type", 
                         disabled=len(st.session_state.selected_power_types_list) >= len(power_types),
                         use_container_width=True)
        
        # 处理添加按钮点击
        if add_button_clicked:
            if power_type_to_add and power_type_to_add not in st.session_state.selected_power_types_list:
                st.session_state.selected_power_types_list.append(power_type_to_add)
        
        # 显示已选择的发电类型和删除按钮
        if st.session_state.selected_power_types_list:
            st.sidebar.write("已选择的发电类型：")
            for idx, pt in enumerate(st.session_state.selected_power_types_list):
                cols = st.sidebar.columns([3, 1])
                cols[0].write(f"{idx+1}. {pt}")
                if cols[1].button("×", key=f"remove_power_{idx}"):
                    st.session_state.selected_power_types_list.pop(idx)
                    st.rerun()
        
        selected_power_types = st.session_state.selected_power_types_list
        
        # 其他筛选项正常显示
        selected_transaction_types = st.sidebar.multiselect("交易类型", transaction_types, default=None)
        selected_transaction_periods = st.sidebar.multiselect("交易周期", transaction_periods, default=None)
        selected_contract_periods = st.sidebar.multiselect("合同执行周期", contract_periods, default=None)
        selected_green_power = st.sidebar.multiselect("是否绿电", green_power_options, default=None)
    
    elif multi_select_dimension == "交易周期":
        # 交易周期作为多选项
        st.sidebar.info("未选择交易周期时，默认使用所有交易周期")
        
        # 使用session state保存已选择的交易周期列表
        if 'selected_periods_list' not in st.session_state:
            st.session_state.selected_periods_list = []
        
        # 添加交易周期下拉框
        period_to_add = st.sidebar.selectbox(
            "添加交易周期",
            [p for p in transaction_periods if p not in st.session_state.selected_periods_list],
            index=0 if transaction_periods else 0,
            disabled=len(st.session_state.selected_periods_list) >= len(transaction_periods)
        )
        
        # 添加按钮
        add_button_clicked = st.sidebar.button("➕ 添加交易周期", key="add_period", 
                         disabled=len(st.session_state.selected_periods_list) >= len(transaction_periods),
                         use_container_width=True)
        
        # 处理添加按钮点击
        if add_button_clicked:
            if period_to_add and period_to_add not in st.session_state.selected_periods_list:
                st.session_state.selected_periods_list.append(period_to_add)
        
        # 显示已选择的交易周期和删除按钮
        if st.session_state.selected_periods_list:
            st.sidebar.write("已选择的交易周期：")
            for idx, p in enumerate(st.session_state.selected_periods_list):
                cols = st.sidebar.columns([3, 1])
                cols[0].write(f"{idx+1}. {p}")
                if cols[1].button("×", key=f"remove_period_{idx}"):
                    st.session_state.selected_periods_list.pop(idx)
                    st.rerun()
        
        selected_transaction_periods = st.session_state.selected_periods_list
        
        # 其他筛选项正常显示
        selected_transaction_types = st.sidebar.multiselect("交易类型", transaction_types, default=None)
        selected_power_types = st.sidebar.multiselect("发电类型", power_types, default=None)
        selected_contract_periods = st.sidebar.multiselect("合同执行周期", contract_periods, default=None)
        selected_green_power = st.sidebar.multiselect("是否绿电", green_power_options, default=None)
    
    elif multi_select_dimension == "合同执行周期":
        # 合同执行周期作为多选项
        st.sidebar.info("未选择合同执行周期时，默认使用所有合同执行周期")
        
        # 使用session state保存已选择的合同执行周期列表
        if 'selected_contract_list' not in st.session_state:
            st.session_state.selected_contract_list = []
        
        # 添加合同执行周期下拉框
        contract_to_add = st.sidebar.selectbox(
            "添加合同执行周期",
            [p for p in contract_periods if p not in st.session_state.selected_contract_list],
            index=0 if contract_periods else 0,
            disabled=len(st.session_state.selected_contract_list) >= len(contract_periods)
        )
        
        # 添加按钮
        add_button_clicked = st.sidebar.button("➕ 添加合同执行周期", key="add_contract", 
                         disabled=len(st.session_state.selected_contract_list) >= len(contract_periods),
                         use_container_width=True)
        
        # 处理添加按钮点击
        if add_button_clicked:
            if contract_to_add and contract_to_add not in st.session_state.selected_contract_list:
                st.session_state.selected_contract_list.append(contract_to_add)
        
        # 显示已选择的合同执行周期和删除按钮
        if st.session_state.selected_contract_list:
            st.sidebar.write("已选择的合同执行周期：")
            for idx, p in enumerate(st.session_state.selected_contract_list):
                cols = st.sidebar.columns([3, 1])
                cols[0].write(f"{idx+1}. {p}")
                if cols[1].button("×", key=f"remove_contract_{idx}"):
                    st.session_state.selected_contract_list.pop(idx)
                    st.rerun()
        
        selected_contract_periods = st.session_state.selected_contract_list
        
        # 其他筛选项正常显示
        selected_transaction_types = st.sidebar.multiselect("交易类型", transaction_types, default=None)
        selected_power_types = st.sidebar.multiselect("发电类型", power_types, default=None)
        selected_transaction_periods = st.sidebar.multiselect("交易周期", transaction_periods, default=None)
        selected_green_power = st.sidebar.multiselect("是否绿电", green_power_options, default=None)
    
    elif multi_select_dimension == "是否绿电":
        # 是否绿电作为多选项
        st.sidebar.info("未选择是否绿电时，默认使用所有绿电状态")
        
        # 使用session state保存已选择的绿电状态列表
        if 'selected_green_list' not in st.session_state:
            st.session_state.selected_green_list = []
        
        # 添加是否绿电下拉框
        green_to_add = st.sidebar.selectbox(
            "添加是否绿电",
            [g for g in green_power_options if g not in st.session_state.selected_green_list],
            index=0 if green_power_options else 0,
            disabled=len(st.session_state.selected_green_list) >= len(green_power_options)
        )
        
        # 添加按钮
        add_button_clicked = st.sidebar.button("➕ 添加是否绿电", key="add_green", 
                         disabled=len(st.session_state.selected_green_list) >= len(green_power_options),
                         use_container_width=True)
        
        # 处理添加按钮点击
        if add_button_clicked:
            if green_to_add and green_to_add not in st.session_state.selected_green_list:
                st.session_state.selected_green_list.append(green_to_add)
        
        # 显示已选择的绿电状态和删除按钮
        if st.session_state.selected_green_list:
            st.sidebar.write("已选择的绿电状态：")
            for idx, g in enumerate(st.session_state.selected_green_list):
                cols = st.sidebar.columns([3, 1])
                cols[0].write(f"{idx+1}. {g}")
                if cols[1].button("×", key=f"remove_green_{idx}"):
                    st.session_state.selected_green_list.pop(idx)
                    st.rerun()
        
        selected_green_power = st.session_state.selected_green_list
        
        # 其他筛选项正常显示
        selected_transaction_types = st.sidebar.multiselect("交易类型", transaction_types, default=None)
        selected_power_types = st.sidebar.multiselect("发电类型", power_types, default=None)
        selected_transaction_periods = st.sidebar.multiselect("交易周期", transaction_periods, default=None)
        selected_contract_periods = st.sidebar.multiselect("合同执行周期", contract_periods, default=None)
    
    # 应用筛选条件
    filtered_df = longterm_df.copy()
    
    if selected_transaction_types:
        filtered_df = filtered_df[filtered_df['交易类型'].isin(selected_transaction_types)]
    
    if selected_power_types:
        filtered_df = filtered_df[filtered_df['发电类型'].isin(selected_power_types)]
    
    if selected_transaction_periods:
        filtered_df = filtered_df[filtered_df['交易周期'].isin(selected_transaction_periods)]
    
    if selected_contract_periods:
        filtered_df = filtered_df[filtered_df['合同执行周期'].isin(selected_contract_periods)]
    
    if selected_green_power:
        filtered_df = filtered_df[filtered_df['是否为绿电交易'].isin(selected_green_power)]
    
    # 获取当前选择的多选项维度的值列表
    multi_select_values = []
    if multi_select_dimension == "交易类型":
        multi_select_values = selected_transaction_types
        multi_select_column = '交易类型'
    elif multi_select_dimension == "发电类型":
        multi_select_values = selected_power_types
        multi_select_column = '发电类型'
    elif multi_select_dimension == "交易周期":
        multi_select_values = selected_transaction_periods
        multi_select_column = '交易周期'
    elif multi_select_dimension == "合同执行周期":
        multi_select_values = selected_contract_periods
        multi_select_column = '合同执行周期'
    elif multi_select_dimension == "是否绿电":
        multi_select_values = selected_green_power
        multi_select_column = '是否为绿电交易'
    
    # 计算基准价与成交电量的乘积
    filtered_df['基准价乘积'] = filtered_df['基准价（不含超净）元/千千瓦时'] * filtered_df['华能成交电量/亿千瓦时']
    
    # 计算华能成交价格与成交电量的乘积
    filtered_df['华能成交价格乘积'] = filtered_df['华能成交价格元/千千瓦时'] * filtered_df['华能成交电量/亿千瓦时']
    
    # 如果选择了多个维度值，分别计算每个值的数据
    if multi_select_values and len(multi_select_values) > 1:
        # 存储每个维度值的结果
        results = []
        
        for value in multi_select_values:
            value_df = filtered_df[filtered_df[multi_select_column] == value].copy()
            
            # 按所属公司分组计算
            value_result = value_df.groupby('所属公司').agg(
                成交电量=('华能成交电量/亿千瓦时', 'sum'),
                基准价乘积总和=('基准价乘积', 'sum'),
                华能成交价格乘积总和=('华能成交价格乘积', 'sum')
            ).reset_index()
            
            # 计算加权基准价和华能成交电价
            value_result[f'{value}_加权基准价'] = value_result.apply(
                lambda row: row['基准价乘积总和'] / row['成交电量'] if row['成交电量'] > 0 else 0,
                axis=1
            )
            value_result[f'{value}_成交电量'] = value_result['成交电量']
            value_result[f'{value}_华能成交电价'] = value_result.apply(
                lambda row: row['华能成交价格乘积总和'] / row['成交电量'] if row['成交电量'] > 0 else 0,
                axis=1
            )
            
            # 只保留需要的列
            value_result = value_result[['所属公司', f'{value}_加权基准价', f'{value}_成交电量', f'{value}_华能成交电价']]
            results.append(value_result)
        
        # 合并所有维度值的结果
        display_result = results[0]
        for i in range(1, len(results)):
            display_result = display_result.merge(results[i], on='所属公司', how='outer')
        
        # 与公司序号表合并，确保所有公司都显示
        if company_order_df is not None:
            display_result = company_order_df.merge(
                display_result,
                left_on='公司',
                right_on='所属公司',
                how='left'
            )
            display_result = display_result.sort_values('序号').reset_index(drop=True)
            # 如果所属公司为NaN，使用公司列的值
            display_result['所属公司'] = display_result['所属公司'].fillna(display_result['公司'])
            # 构建列顺序
            final_columns = ['序号', '所属公司']
            for value in multi_select_values:
                final_columns.extend([f'{value}_加权基准价', f'{value}_成交电量', f'{value}_华能成交电价'])
            display_result = display_result[final_columns]
        else:
            display_result = display_result[['所属公司'] + [col for v in multi_select_values for col in [f'{v}_加权基准价', f'{v}_成交电量', f'{v}_华能成交电价']]]
    
    else:
        # 单一维度值或未选择维度值的情况（原有逻辑）
        # 按所属公司分组计算
        result = filtered_df.groupby('所属公司').agg(
            成交电量=('华能成交电量/亿千瓦时', 'sum'),
            基准价乘积总和=('基准价乘积', 'sum'),
            华能成交价格乘积总和=('华能成交价格乘积', 'sum')
        ).reset_index()
        
        # 计算加权基准价和华能成交电价
        result['加权基准价'] = result.apply(
            lambda row: row['基准价乘积总和'] / row['成交电量'] if row['成交电量'] > 0 else 0,
            axis=1
        )
        
        result['华能成交电价'] = result.apply(
            lambda row: row['华能成交价格乘积总和'] / row['成交电量'] if row['成交电量'] > 0 else 0,
            axis=1
        )
        
        # 与公司序号表合并，确保所有公司都显示
        if company_order_df is not None:
            display_result = company_order_df.merge(
                result,
                left_on='公司',
                right_on='所属公司',
                how='left'
            )
            display_result = display_result.sort_values('序号').reset_index(drop=True)
            # 如果所属公司为NaN，使用公司列的值
            display_result['所属公司'] = display_result['所属公司'].fillna(display_result['公司'])
            display_result = display_result[['序号', '所属公司', '加权基准价', '成交电量', '华能成交电价']]
        else:
            display_result = result[['所属公司', '加权基准价', '成交电量', '华能成交电价']]
    
    # 显示数据
    st.subheader(f"透视表结果（共 {len(display_result)} 行）")
    st.dataframe(display_result, width='stretch')
    
    # 可视化
    if multi_select_values and len(multi_select_values) > 1:
        # 多维度值对比图表
        st.subheader(f"📊 各{multi_select_dimension}成交电量对比")
        chart_data = display_result.set_index('所属公司')
        chart_columns = [f'{v}_成交电量' for v in multi_select_values]
        chart_data = chart_data[chart_columns]
        chart_data.columns = multi_select_values
        st.bar_chart(chart_data)
    else:
        col1, col2 = st.columns(2)
        with col1:
            st.subheader("📊 成交电量统计")
            chart_data = display_result[display_result['成交电量'] > 0]
            if not chart_data.empty:
                st.bar_chart(chart_data.set_index('所属公司')['成交电量'])
    
        with col2:
            st.subheader("📊 华能成交电价")
            chart_data = display_result[display_result['华能成交电价'] > 0]
            if not chart_data.empty:
                st.bar_chart(chart_data.set_index('所属公司')['华能成交电价'])
    
    # 数据下载
    st.header("📥 数据下载")
    csv_data = display_result.to_csv(index=False, encoding='utf-8-sig')
    st.download_button(
        label="下载透视表数据",
        data=csv_data,
        file_name="中长期台账_透视表.csv",
        mime="text/csv"
    )

# 地区现货分析功能
def regional_spot_analysis():
    st.header("地区现货分析")
    
    spot_df = load_spot_data()
    
    if spot_df is None:
        st.error("未找到现货交易数据，请先运行ETL脚本生成数据！")
        return
    
    # 读取煤机数据
    coal_spot_df = spot_df[spot_df['发电类型'] == '煤机'].copy()
    
    if coal_spot_df.empty:
        st.error("未找到煤机现货数据！")
        return
    
    # 定义区域映射
    region_mapping = {
        '黑龙江': '东北区域',
        '吉林': '东北区域',
        '辽宁': '东北区域',
        '蒙东': '东北区域',
        '陕西': '西北区域',
        '甘肃': '西北区域',
        '新疆': '西北区域',
        '宁夏': '西北区域',
        '青海': '西北区域',
        '山东': '华北区域',
        '北方': '华北区域',
        '河北': '华北区域',
        '上海': '华东区域',
        '浙江': '华东区域',
        '江苏': '华东区域',
        '安徽': '华东区域',
        '福建': '华东区域',
        '河南': '华中区域',
        '湖北': '华中区域',
        '湖南': '华中区域',
        '江西': '华中区域',
        '四川': '华中区域',
        '重庆': '华中区域',
        '广东': '南方区域',
        '云南': '南方区域',
        '广西': '南方区域',
        '贵州': '南方区域',
        '海南': '南方区域'
    }
    
    # 区域列表
    regions = ['全国', '东北区域', '西北区域', '华北区域', '华东区域', '华中区域', '南方区域']
    
    # 侧边栏筛选条件
    st.sidebar.subheader("筛选条件")
    selected_region = st.sidebar.selectbox(
        "选择区域",
        regions,
        index=0,
        help="选择全国显示6个区域的图表，选择具体区域显示该区域内各省份的图表"
    )
    
    # 将公司映射到区域
    coal_spot_df['区域'] = coal_spot_df['公司'].map(region_mapping)
    
    # 按周统计（周六到周五）
    # 首先确定日期的星期几（0=周一, 1=周二, ..., 5=周六, 6=周日）
    coal_spot_df['星期几'] = coal_spot_df['日期'].dt.dayofweek
    
    # 创建周标识：每周从周六开始到周五结束
    # 如果是周六（5）或周日（6），属于本周
    # 如果是周一到周五（0-4），属于上周
    def get_week_id(date, dayofweek):
        if dayofweek >= 5:  # 周六或周日
            # 周六和周日归入本周
            days_since_saturday = (dayofweek - 5) % 7
            week_start = date - pd.Timedelta(days=days_since_saturday)
        else:  # 周一到周五
            # 周一到周五归入上周
            days_until_saturday = (5 - dayofweek) % 7
            week_start = date + pd.Timedelta(days=days_until_saturday) - pd.Timedelta(weeks=1)
        return week_start.strftime('%Y-%m-%d')
    
    coal_spot_df['周开始日期'] = coal_spot_df.apply(
        lambda row: get_week_id(row['日期'], row['星期几']), axis=1
    )
    
    # 获取周结束日期（周五）
    coal_spot_df['周结束日期'] = coal_spot_df['周开始日期'].apply(
        lambda x: (pd.to_datetime(x) + pd.Timedelta(days=6)).strftime('%Y-%m-%d')
    )
    
    # 生成周标签（如 "2026.5.16-2026.5.22"）
    coal_spot_df['周标签'] = coal_spot_df['周开始日期'] + ' - ' + coal_spot_df['周结束日期']
    
    if selected_region == '全国':
        # 显示6个区域的图表
        # 按区域和周进行分组统计
        weekly_stats = coal_spot_df.groupby(['区域', '周开始日期', '周标签']).agg(
            平均实时均价=('统一结算点实时均价', 'mean')
        ).reset_index()
        
        # 按周排序
        weekly_stats = weekly_stats.sort_values('周开始日期')
        
        # 数据透视表：区域为行，周为列
        pivot_table = weekly_stats.pivot(
            index='区域',
            columns='周标签',
            values='平均实时均价'
        )
        
        # 按区域排序
        region_order = ['东北区域', '西北区域', '华北区域', '华东区域', '华中区域', '南方区域']
        pivot_table = pivot_table.reindex(region_order)
        
        # 显示数据透视表
        st.subheader("各区域煤电现货统一结算点实时均价（周平均）")
        st.dataframe(pivot_table, width='stretch')
        
        # 可视化
        st.subheader("📊 各区域周度均价趋势")
        chart_data = pivot_table.T  # 转置，使周为X轴
        if not chart_data.empty:
            st.line_chart(chart_data)
        
        # 数据下载
        st.header("📥 数据下载")
        csv_data = pivot_table.to_csv(encoding='utf-8-sig')
        st.download_button(
            label="下载地区现货分析数据",
            data=csv_data,
            file_name="地区现货分析_周度均价.csv",
            mime="text/csv"
        )
    else:
        # 显示选中区域内各省份的图表
        # 筛选选中区域的数据
        region_df = coal_spot_df[coal_spot_df['区域'] == selected_region].copy()
        
        if region_df.empty:
            st.error(f"{selected_region}没有数据！")
            return
        
        # 按公司（省份）和周进行分组统计
        weekly_stats = region_df.groupby(['公司', '周开始日期', '周标签']).agg(
            平均实时均价=('统一结算点实时均价', 'mean')
        ).reset_index()
        
        # 按周排序
        weekly_stats = weekly_stats.sort_values('周开始日期')
        
        # 数据透视表：公司为行，周为列
        pivot_table = weekly_stats.pivot(
            index='公司',
            columns='周标签',
            values='平均实时均价'
        )
        
        # 显示数据透视表
        st.subheader(f"{selected_region}各省份煤电现货统一结算点实时均价（周平均）")
        st.dataframe(pivot_table, width='stretch')
        
        # 可视化
        st.subheader(f"📊 {selected_region}各省份周度均价趋势")
        chart_data = pivot_table.T  # 转置，使周为X轴
        if not chart_data.empty:
            st.line_chart(chart_data)
        
        # 数据下载
        st.header("📥 数据下载")
        csv_data = pivot_table.to_csv(encoding='utf-8-sig')
        st.download_button(
            label=f"下载{selected_region}现货分析数据",
            data=csv_data,
            file_name=f"{selected_region}_现货分析_周度均价.csv",
            mime="text/csv"
        )

# 均价展示功能
def average_price_display():
    st.header("现货均价显示")
    
    spot_df = load_spot_data()
    
    if spot_df is None:
        st.error("未找到现货交易数据，请先运行ETL脚本生成数据！")
        return
    
    # 读取煤机数据
    coal_spot_df = spot_df[spot_df['发电类型'] == '煤机'].copy()
    
    if coal_spot_df.empty:
        st.error("未找到煤机现货数据！")
        return
    
    # 侧边栏筛选条件
    st.sidebar.subheader("日期筛选")
    
    # 获取日期范围
    min_date = coal_spot_df['日期'].min().date()
    max_date = coal_spot_df['日期'].max().date()
    
    # 起止日期选择
    col1, col2 = st.sidebar.columns(2)
    with col1:
        start_date = st.date_input("开始日期", value=min_date, min_value=min_date, max_value=max_date)
    with col2:
        end_date = st.date_input("结束日期", value=max_date, min_value=min_date, max_value=max_date)
    
    # 筛选日期范围内的数据
    filtered_df = coal_spot_df[
        (coal_spot_df['日期'].dt.date >= start_date) & 
        (coal_spot_df['日期'].dt.date <= end_date)
    ].copy()
    
    if filtered_df.empty:
        st.warning(f"在 {start_date} 到 {end_date} 范围内没有数据！")
        return
    
    # 计算每个所属公司的实时均价统计
    company_stats = filtered_df.groupby('公司').agg(
        数据条数=('统一结算点实时均价', 'count'),
        平均实时均价=('统一结算点实时均价', 'mean'),
        最高价=('统一结算点实时均价', 'max'),
        最低价=('统一结算点实时均价', 'min')
    ).reset_index()
    
    # 与公司序号表合并，确保所有公司都显示
    if company_order_df is not None:
        display_result = company_order_df.merge(
            company_stats,
            left_on='公司',
            right_on='公司',
            how='outer'
        )
        display_result = display_result.sort_values('序号').reset_index(drop=True)
        # 如果公司为NaN，使用公司列的值
        display_result['公司'] = display_result['公司'].fillna(display_result['公司'])
        display_result = display_result[['序号', '公司', '数据条数', '平均实时均价', '最高价', '最低价']]
    else:
        display_result = company_stats[['公司', '数据条数', '平均实时均价', '最高价', '最低价']]
    
    # 显示数据透视表
    st.subheader(f"各所属公司实时均价统计（{start_date} 至 {end_date}）")
    st.dataframe(display_result, width='stretch')
    
    # 可视化：平均实时均价柱状图
    st.subheader("📊 各所属公司平均实时均价")
    chart_data = display_result.dropna(subset=['平均实时均价'])
    if not chart_data.empty:
        st.bar_chart(chart_data.set_index('公司')['平均实时均价'])
    
    # 统计信息
    st.subheader("📈 统计汇总")
    
    # 找出最高价和最低价的公司
    max_price_row = display_result.loc[display_result['最高价'].idxmax()] if display_result['最高价'].notna().any() else None
    min_price_row = display_result.loc[display_result['最低价'].idxmin()] if display_result['最低价'].notna().any() else None
    
    col1, col2 = st.columns(2)
    
    with col1:
        if max_price_row is not None and pd.notna(max_price_row['最高价']):
            st.metric(
                "最高价",
                f"{max_price_row['最高价']:.2f} 元/兆瓦时",
                f"{max_price_row['公司']}"
            )
        else:
            st.metric("最高价", "无数据")
    
    with col2:
        if min_price_row is not None and pd.notna(min_price_row['最低价']):
            st.metric(
                "最低价",
                f"{min_price_row['最低价']:.2f} 元/兆瓦时",
                f"{min_price_row['公司']}"
            )
        else:
            st.metric("最低价", "无数据")
    
    # 数据下载
    st.header("📥 数据下载")
    csv_data = display_result.to_csv(index=False, encoding='utf-8-sig')
    st.download_button(
        label="下载均价统计数据",
        data=csv_data,
        file_name=f"均价统计_{start_date}_{end_date}.csv",
        mime="text/csv"
    )

# 根据选择的功能模块执行
if function_module == "中长期台账":
    longterm_ledger()
elif function_module == "现货电价跟踪":
    spot_coal_price_tracking()
elif function_module == "地区现货分析":
    regional_spot_analysis()
else:
    average_price_display()