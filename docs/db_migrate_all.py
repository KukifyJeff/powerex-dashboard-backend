import pandas as pd
import os
import pymysql
import re
from sqlalchemy import create_engine, text
from datetime import datetime

# 获取脚本所在目录的父目录作为项目根目录
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)

# 数据库配置
DB_CONFIG = {
    'host': 'localhost',
    'user': 'admin',
    'password': 'ChngPowerEx_2026',
    'database': 'power_trading',
    'port': 3306
}

# 查找表数据
TRANSACTION_TYPES = [
    (1, '直接'),
    (2, '外送'),
    (3, '代理购电'),
    (4, '发电权转让')
]

TRANSACTION_PERIODS = [
    (1, '年度'),
    (2, '年度分解'),
    (3, '多月'),
    (4, '多月分解'),
    (5, '月度'),
    (6, '月度分解')
]

GEN_TYPES = [
    (1, '煤电'),
    (2, '光伏'),
    (3, '风电'),
    (4, '燃气'),
    (5, '水电'),
    (6, '核电')
]

def convert_percent(value):
    try:
        value = str(value).strip()
        if value.endswith('%'):
            return float(value[:-1]) / 100
        return float(value)
    except (ValueError, TypeError):
        return None

def parse_contract_period(period_str, default_year=2026):
    import calendar
    try:
        if pd.isna(period_str) or not period_str:
            return None, None, default_year
        period_str = str(period_str).strip()
        if period_str == 'nan':
            return None, None, default_year
        detected_year = default_year
        year_match = re.search(r'(\d{4})', period_str)
        if year_match:
            detected_year = int(year_match.group(1))
            period_str = re.sub(r'\d{4}年', '', period_str)
        period_str = period_str.replace('月', '').replace('至', '-')
        if '-' in period_str:
            parts = period_str.split('-')
            start_month = int(parts[0])
            end_month = int(parts[1])
        else:
            start_month = int(period_str)
            end_month = start_month
        start_day = 1
        end_day = calendar.monthrange(detected_year, end_month)[1]
        start_date = f"{detected_year}-{start_month:02d}-{start_day:02d}"
        end_date = f"{detected_year}-{end_month:02d}-{end_day:02d}"
        return start_date, end_date, detected_year
    except (ValueError, TypeError, IndexError):
        return None, None, default_year

def parse_transaction_date(date_str, default_year=2026):
    try:
        if pd.isna(date_str) or not date_str:
            return None
        date_str = str(date_str).strip()
        if date_str == 'nan':
            return None
        match = re.search(r'(\d{4})[年\.](\d{1,2})(?:月|$)', date_str)
        if match:
            year = int(match.group(1))
            month = int(match.group(2))
            return f"{year}-{month:02d}-01"
        parsed = pd.to_datetime(date_str, errors='coerce')
        if pd.notna(parsed):
            return parsed.strftime('%Y-%m-%d')
        return None
    except Exception:
        return None

def check_prerequisite_data(engine):
    """检查companies和lookup表是否有数据"""
    print("检查基础数据...")
    with engine.connect() as conn:
        companies_count = conn.execute(text("SELECT COUNT(*) FROM companies")).first()[0]
        tt_count = conn.execute(text("SELECT COUNT(*) FROM transaction_types")).first()[0]
        tp_count = conn.execute(text("SELECT COUNT(*) FROM transaction_periods")).first()[0]
        gt_count = conn.execute(text("SELECT COUNT(*) FROM gen_types")).first()[0]
    
    if companies_count == 0:
        print("  ❌ companies表为空，请先运行db_migrate.py初始化公司数据")
        return False
    if tt_count == 0 or tp_count == 0 or gt_count == 0:
        print("  ❌ 查找表为空，请先运行db_migrate.py初始化查找表数据")
        return False
    
    print(f"  ✅ companies: {companies_count}条")
    print(f"  ✅ transaction_types: {tt_count}条")
    print(f"  ✅ transaction_periods: {tp_count}条")
    print(f"  ✅ gen_types: {gt_count}条")
    return True

def clear_longterm_table(engine):
    """清空longterm_transactions表并重置AUTO_INCREMENT"""
    print("清空longterm_transactions表...")
    with engine.connect() as conn:
        conn.execute(text("SET FOREIGN_KEY_CHECKS = 0"))
        conn.execute(text("TRUNCATE TABLE longterm_transactions"))
        conn.execute(text("SET FOREIGN_KEY_CHECKS = 1"))
        conn.commit()
    print("  [OK] 已清空并重置AUTO_INCREMENT")

def clear_spot_table(engine):
    """清空spot_transactions表并重置AUTO_INCREMENT"""
    print("清空spot_transactions表...")
    with engine.connect() as conn:
        conn.execute(text("SET FOREIGN_KEY_CHECKS = 0"))
        conn.execute(text("TRUNCATE TABLE spot_transactions"))
        conn.execute(text("SET FOREIGN_KEY_CHECKS = 1"))
        conn.commit()
    print("  [OK] 已清空并重置AUTO_INCREMENT")

def get_company_id(engine, company_name):
    with engine.connect() as conn:
        result = conn.execute(text(f"SELECT id FROM companies WHERE name = '{company_name}'")).first()
        return result[0] if result else None

def get_gen_type_id(engine, gen_type_name):
    gen_type_mapping = {
        '煤电': 1,
        '光伏': 2,
        '风电': 3,
        '燃气': 4,
        '水电': 5,
        '核电': 6,
        '煤机': 1
    }
    return gen_type_mapping.get(gen_type_name)

def get_transaction_type_id(engine, type_name):
    type_mapping = {
        '直接': 1,
        '外送': 2,
        '代理购电': 3,
        '发电权转让': 4
    }
    return type_mapping.get(type_name)

def get_transaction_period_id(engine, period_name):
    period_mapping = {
        '年度': 1,
        '年度分解': 2,
        '多月': 3,
        '多月分解': 4,
        '月度': 5,
        '月度分解': 6
    }
    return period_mapping.get(period_name)

def clean_numeric_value(value):
    if pd.isna(value):
        return None
    value_str = str(value).strip()
    if value_str in ['-', '/', '（公告未披露）', 'nan', 'NaN', '']:
        return None
    try:
        return float(value_str)
    except (ValueError, TypeError):
        return None

def import_longterm_data_for_year(engine, year):
    """导入指定年份的中长期交易数据"""
    print(f"\n开始导入 {year} 年中长期交易数据...")
    source_dir = os.path.join(PROJECT_ROOT, '新版统计', str(year))
    
    if not os.path.exists(source_dir):
        print(f"  ❌ 目录不存在: {source_dir}")
        return 0, [], []
    
    total_imported = 0
    failed_files = []
    nan_files = []
    
    for f in os.listdir(source_dir):
        if f.startswith('~$'):
            continue
        if f.lower().endswith(('.xlsx', '.xls')):
            filepath = os.path.join(source_dir, f)
            try:
                if filepath.lower().endswith('.xls'):
                    df = pd.read_excel(filepath, header=None)
                else:
                    df = pd.read_excel(filepath, header=None, engine='openpyxl')
                
                header_row = 2
                for i in range(min(10, len(df))):
                    first_val = str(df.iloc[i, 0]).strip()
                    if first_val == '序号':
                        header_row = i
                        break
                
                df = df.iloc[header_row:].reset_index(drop=True)
                new_header = df.iloc[0]
                df = df[1:].reset_index(drop=True)
                df.columns = new_header
                
                cols = []
                seen = {}
                for col in df.columns:
                    col_str = str(col).replace('\n', '').strip()
                    if col_str not in seen:
                        seen[col_str] = 0
                        cols.append(col_str)
                    else:
                        seen[col_str] += 1
                        cols.append(f"{col_str}_{seen[col_str]}")
                df.columns = cols
                
                df = df[df['所属公司'].notna() & (df['所属公司'] != '所属公司')]
                
                df['company_id'] = df['所属公司'].apply(lambda x: get_company_id(engine, str(x).strip()))
                
                df['gen_type_id'] = df['发电类型'].apply(lambda x: get_gen_type_id(engine, str(x).strip()))
                
                df['transaction_type_id'] = df['交易类型'].apply(lambda x: get_transaction_type_id(engine, str(x).strip()))
                
                df['transaction_period_id'] = df['交易周期'].apply(lambda x: get_transaction_period_id(engine, str(x).strip()))
                
                df['is_green'] = df['是否为绿电交易'].apply(lambda x: True if str(x).strip() == '是' else False)
                
                df['is_cheap'] = df.get('是否为平价项目', '').apply(lambda x: True if str(x).strip() == '是' else False)
                
                if '合同执行周期' in df.columns:
                    contract_periods = df['合同执行周期'].apply(lambda x: parse_contract_period(x, year))
                    df['contract_start_date'] = contract_periods.apply(lambda x: x[0])
                    df['contract_end_date'] = contract_periods.apply(lambda x: x[1])
                    df['transaction_start_year'] = contract_periods.apply(lambda x: x[2])
                    df['transaction_end_year'] = contract_periods.apply(lambda x: x[2])
                
                if '交易日期' in df.columns:
                    df['交易日期'] = df['交易日期'].apply(lambda x: parse_transaction_date(x, year))
                
                column_mapping = {
                    '序号': 'transaction_id',
                    '交易日期': 'transaction_date',
                    '交易名称': 'transaction_name',
                    '受端省份': 'outsend_province',
                    '区域': 'place',
                    '基准价（不含超净）元/千千瓦时': 'base_price',
                    '基准价': 'base_price',
                    '市场规模/亿千瓦时': 'market_size',
                    '市场参与装机容量/万千瓦': 'market_participation_capacity',
                    '市场交易均价': 'market_avg_price',
                    '市场交易均价（元/千千瓦时）': 'market_avg_price',
                    '市场交易均价(元/千千瓦时)': 'market_avg_price',
                    '市场交易均价/元/千千瓦时': 'market_avg_price',
                    '华能参与装机/万千瓦': 'chng_participation_capacity',
                    '华能成交电量/亿千瓦时': 'chng_transaction_amount',
                    '华能成交电量': 'chng_transaction_amount',
                    '华能成交价格 元/千千瓦时': 'chng_avg_price',
                    '华能成交价格': 'chng_avg_price',
                    '华能成交价格（元/千千瓦时）': 'chng_avg_price',
                    '华能成交价格(元/千千瓦时)': 'chng_avg_price',
                    '华能成交价格元/千千瓦时': 'chng_avg_price',
                    '环境溢价元/千千瓦时': 'env_premium',
                    '数据来源': 'data_source',
                    '备注': 'note'
                }
                df = df.rename(columns=column_mapping)
                
                df['data_source'] = f
                
                numeric_columns = [
                    'base_price', 'market_size', 'market_participation_capacity',
                    'market_avg_price', 'chng_participation_capacity',
                    'chng_transaction_amount', 'chng_avg_price', 'env_premium'
                ]
                for col in numeric_columns:
                    if col in df.columns:
                        df[col] = df[col].apply(clean_numeric_value)
                
                target_columns = [
                    'transaction_id', 'company_id', 'place', 'transaction_date', 'transaction_name',
                    'transaction_type_id', 'gen_type_id', 'transaction_period_id',
                    'transaction_start_year', 'transaction_end_year',
                    'contract_start_date', 'contract_end_date',
                    'is_green', 'is_cheap', 'base_price',
                    'outsend_province', 'market_size', 'market_participation_capacity',
                    'market_avg_price', 'chng_participation_capacity',
                    'chng_transaction_amount', 'chng_avg_price', 'env_premium',
                    'data_source', 'note'
                ]
                df = df[[col for col in target_columns if col in df.columns]]
                
                nan_columns = []
                key_columns = ['发电类型', '交易类型', '交易周期', '所属公司']
                for col in key_columns:
                    if col in df.columns and df[col].isna().any():
                        nan_columns.append(col)
                
                if nan_columns:
                    nan_files.append({'file': f, 'year': year, 'columns': nan_columns})
                
                df = df.dropna(subset=['company_id', 'gen_type_id'])
                
                df.to_sql('longterm_transactions', engine, if_exists='append', index=False)
                total_imported += len(df)
                print(f"  导入: {f} ({len(df)}行)")
            except Exception as e:
                print(f"  跳过: {f} - {e}")
                failed_files.append((f, str(e)))
    
    print(f"成功导入 {total_imported} 条 {year} 年中长期交易数据")
    return total_imported, failed_files, nan_files

def import_spot_data(engine):
    """导入现货交易数据"""
    print("\n开始导入现货交易数据...")
    spot_dir = os.path.join(PROJECT_ROOT, '现货电价周跟踪', '2026')
    
    power_type_mapping = {
        '煤机': '煤电',
        '风电': '风电',
        '光伏': '光伏'
    }
    
    total_imported = 0
    failed_files = []
    
    for power_type in ['煤机', '风电', '光伏']:
        type_dir = os.path.join(spot_dir, power_type)
        if not os.path.exists(type_dir):
            print(f"  [SKIP] {power_type}: 目录不存在")
            continue
        
        for f in os.listdir(type_dir):
            if f.startswith('~$'):
                continue
            if f.lower().endswith(('.xlsx', '.xls')):
                filepath = os.path.join(type_dir, f)
                try:
                    if filepath.lower().endswith('.xls'):
                        df = pd.read_excel(filepath, header=None)
                    else:
                        df = pd.read_excel(filepath, header=None, engine='openpyxl')
                    
                    header_row = 2
                    for i in range(min(10, len(df))):
                        first_val = str(df.iloc[i, 0]).strip()
                        if first_val == '日期':
                            header_row = i
                            break
                    
                    df = df.iloc[header_row:].reset_index(drop=True)
                    new_header = df.iloc[0]
                    df = df[1:].reset_index(drop=True)
                    df.columns = new_header
                    
                    cols = []
                    seen = {}
                    for col in df.columns:
                        col_str = str(col).replace('\n', '').strip()
                        if col_str not in seen:
                            seen[col_str] = 0
                            cols.append(col_str)
                        else:
                            seen[col_str] += 1
                            cols.append(f"{col_str}_{seen[col_str]}")
                    df.columns = cols
                    
                    df['company_id'] = df['公司'].apply(lambda x: get_company_id(engine, str(x).strip()))
                    
                    gen_type_name = power_type_mapping.get(power_type, power_type)
                    df['gen_type_id'] = get_gen_type_id(engine, gen_type_name)
                    
                    if '日期' in df.columns:
                        df['date'] = pd.to_datetime(df['日期'], errors='coerce').dt.date
                    
                    if '中长期持仓率' in df.columns:
                        df['中长期持仓率'] = df['中长期持仓率'].apply(convert_percent)
                        df['中长期持仓率'] = df['中长期持仓率'].apply(lambda x: min(x, 99.9999) if x is not None else None)
                    
                    column_mapping = {
                        '上网电量': 'gen_amount',
                        '中长期合约电量': 'longterm_amount',
                        '中长期合约电价': 'longterm_price',
                        '中长期持仓率': 'longterm_percent',
                        '统一结算点实时均价': 'spot_price',
                        '日清分电价': 'chng_spot_price',
                        '数据来源': 'data_source',
                        '备注': 'note'
                    }
                    df = df.rename(columns=column_mapping)
                    
                    df['data_source'] = f
                    
                    target_columns = [
                        'company_id', 'date', 'gen_type_id', 'gen_amount',
                        'longterm_amount', 'longterm_price', 'longterm_percent', 'spot_price',
                        'chng_spot_price', 'data_source', 'note'
                    ]
                    df = df[[col for col in target_columns if col in df.columns]]
                    
                    df = df.dropna(subset=['company_id', 'gen_type_id', 'date'])
                    
                    df.to_sql('spot_transactions', engine, if_exists='append', index=False)
                    total_imported += len(df)
                    print(f"  导入 {power_type}: {f} ({len(df)}行)")
                except Exception as e:
                    print(f"  跳过 {power_type}: {f} - {e}")
                    failed_files.append((f, str(e)))
    
    print(f"成功导入 {total_imported} 条现货交易数据")
    return total_imported, failed_files

def main():
    print("=" * 60)
    print("电力交易数据全量导入工具")
    print("版本: 1.1")
    print("导入范围: 2023-2026年中长期 + 2026年现货")
    print("=" * 60)
    
    engine = create_engine(
        f"mysql+pymysql://{DB_CONFIG['user']}:{DB_CONFIG['password']}@{DB_CONFIG['host']}:{DB_CONFIG['port']}/{DB_CONFIG['database']}",
        pool_pre_ping=True
    )
    
    if not check_prerequisite_data(engine):
        print("\n请先运行 db_migrate.py 初始化基础数据后再执行本脚本")
        return
    
    clear_longterm_table(engine)
    clear_spot_table(engine)
    
    all_nan_files = []
    all_failed_files = []
    total_all = 0
    
    for year in [2023, 2024, 2025, 2026]:
        count, failed, nan = import_longterm_data_for_year(engine, year)
        total_all += count
        all_failed_files.extend(failed)
        all_nan_files.extend(nan)
    
    spot_count, spot_failed = import_spot_data(engine)
    total_all += spot_count
    all_failed_files.extend(spot_failed)
    
    print("\n" + "=" * 60)
    if all_failed_files:
        print("导入失败的文件：")
        for filename, error in all_failed_files:
            print(f"  - {filename}")
            print(f"    错误: {error[:100]}...")
        print("=" * 60)
    
    print("\n" + "=" * 60)
    if all_nan_files:
        print("包含nan值的文件：")
        for item in all_nan_files:
            print(f"  - {item['year']}年/{item['file']}")
            print(f"    nan列: {', '.join(item['columns'])}")
        print("=" * 60)
    
    print(f"\n{'-' * 60}")
    print(f"全量导入完成！总计导入 {total_all} 条记录")
    print(f"{'-' * 60}")

if __name__ == '__main__':
    main()