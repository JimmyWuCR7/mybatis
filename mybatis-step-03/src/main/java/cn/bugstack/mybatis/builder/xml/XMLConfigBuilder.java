package cn.bugstack.mybatis.builder.xml;

import cn.bugstack.mybatis.builder.BaseBuilder;
import cn.bugstack.mybatis.io.Resources;
import cn.bugstack.mybatis.mapping.MappedStatement;
import cn.bugstack.mybatis.mapping.SqlCommandType;
import cn.bugstack.mybatis.session.Configuration;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.xml.sax.InputSource;

import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author 小傅哥，微信：fustack
 * @description XML配置构建器，建造者模式，继承BaseBuilder
 * @date 2022/04/06
 * @github https://github.com/fuzhengwei
 * @copyright 公众号：bugstack虫洞栈 | 博客：https://bugstack.cn - 沉淀、分享、成长，让自己和他人都能有所收获！
 */
public class XMLConfigBuilder extends BaseBuilder {

    /**
     * XML文档的根节点（<mappers>节点）
     * 用于后续解析其中的所有<mapper>子节点
     */
    private Element root;

    /**
     * 构造函数：接收一个Reader（通常对应mybatis-config.xml配置文件）
     * 主要做了两件事：
     *   1. 调用父类构造器，创建一个空的Configuration对象
     *   2. 使用dom4j的SAXReader读取XML内容，得到根节点
     *
     * @param reader 配置文件读取器，通常对应 mybatis-config.xml
     */
    public XMLConfigBuilder(Reader reader) {
        // 1. 调用父类初始化Configuration
        super(new Configuration());
        // 2. dom4j 处理 xml
        //    SAXReader是dom4j提供的XML解析器，采用SAX方式解析（逐行读取，性能好、内存占用低）
        SAXReader saxReader = new SAXReader();
        try {
            // saxReader.read() 解析XML文档，返回Document对象（整个XML的内存模型）
            // InputSource用于包装Reader，让SAXParser能正确读取字符流
            Document document = saxReader.read(new InputSource(reader));
            // 获取XML的根节点，在本例中就是 <mappers> 节点
            root = document.getRootElement();
        } catch (DocumentException e) {
            // XML解析失败时，打印异常堆栈
            // 生产环境建议改成抛出自定义异常，如throw new MyBatisException("解析XML失败", e);
            e.printStackTrace();
        }
    }

    /**
     * 解析配置；类型别名、插件、对象工厂、对象包装工厂、设置、环境、类型转换、映射器
     * 这是入口方法，会解析整个XML配置文件，提取所有Mapper信息并注册到Configuration中
     *
     * @return Configuration 配置对象（包含所有解析后的SQL映射信息）
     */
    public Configuration parse() {
        try {
            // 解析<mappers>节点下的所有<mapper>节点
            // root.element("mappers") 获取 <mappers> 子节点
            mapperElement(root.element("mappers"));
        } catch (Exception e) {
            // 解析Mapper配置失败时，抛出运行时异常，并附带原始异常信息
            // 这是MyBatis的惯用做法，便于定位问题根因
            throw new RuntimeException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
        }
        return configuration;
    }

    /**
     * 解析所有<mapper>节点
     * 
     * 每个<mapper>节点对应一个Mapper XML文件（如UserMapper.xml）
     * 该方法会：
     *   1. 遍历所有<mapper>节点
     *   2. 读取每个Mapper XML文件
     *   3. 解析其中的SQL语句（select/insert/update/delete）
     *   4. 将SQL信息包装成MappedStatement对象存入Configuration
     *   5. 注册Mapper接口到MapperRegistry
     *
     * @param mappers <mappers>节点，包含多个<mapper>子节点
     */
    private void mapperElement(Element mappers) throws Exception {
        // mappers.elements("mapper") 获取所有<mapper>子节点，返回List
        List<Element> mapperList = mappers.elements("mapper");
        
        // 遍历每一个<mapper>节点
        for (Element e : mapperList) {
            // 获取<mapper resource="mapper/User_Mapper.xml"> 中的 resource 属性
            String resource = e.attributeValue("resource");
            
            // 通过Resources工具类读取classpath下的XML文件，返回Reader
            Reader reader = Resources.getResourceAsReader(resource);
            
            // 再次使用SAXReader解析这个Mapper XML文件
            SAXReader saxReader = new SAXReader();
            Document document = saxReader.read(new InputSource(reader));
            // 获取Mapper XML的根节点，对于UserMapper.xml来说就是 <mapper namespace="...">
            Element root = document.getRootElement();
            
            // 获取命名空间，这个很重要！
            // namespace = "cn.bugstack.mybatis.dao.UserMapper"
            // 用于将SQL语句和Mapper接口方法关联起来
            String namespace = root.attributeValue("namespace");

            // ========== 解析 SELECT 节点 ==========
            // root.elements("select") 获取该Mapper下所有的<select>节点
            List<Element> selectNodes = root.elements("select");
            for (Element node : selectNodes) {
                // id = "selectById"，对应Mapper接口中的方法名
                String id = node.attributeValue("id");
                // parameterType = "java.lang.Long"，入参类型
                String parameterType = node.attributeValue("parameterType");
                // resultType = "cn.bugstack.mybatis.domain.User"，返回结果类型
                String resultType = node.attributeValue("resultType");
                // sql = "SELECT * FROM user WHERE id = #{id}"，SQL语句原文
                String sql = node.getText();

                // ========== 处理占位符：#{} 转换为 ? ==========
                // MyBatis使用?作为预编译SQL的参数占位符
                // 这里把 "SELECT * FROM user WHERE id = #{id}" 转成 "SELECT * FROM user WHERE id = ?"
                Map<Integer, String> parameter = new HashMap<>();
                // 正则匹配 #{} 包裹的内容，如 #{id} 匹配到 id
                Pattern pattern = Pattern.compile("(#\\{(.*?)})");
                Matcher matcher = pattern.matcher(sql);
                // 循环找到所有的 #{} 占位符
                for (int i = 1; matcher.find(); i++) {
                    // g1 = "#{id}"，完整匹配
                    // g2 = "id"，只提取大括号内的参数名
                    String g1 = matcher.group(1);
                    String g2 = matcher.group(2);
                    // parameterMap: {1 -> "id"}，记录第1个?对应的参数名
                    parameter.put(i, g2);
                    // sql.replace("#{id}", "?") 把 #{id} 替换成 ?
                    sql = sql.replace(g1, "?");
                }

                // ========== 构建 MappedStatement ==========
                // msId = namespace + "." + id，如 "cn.bugstack.mybatis.dao.UserMapper.selectById"
                // 这是MyBatis中SQL语句的唯一标识符
                String msId = namespace + "." + id;
                // nodeName = "select"，转换为枚举就是 SqlCommandType.SELECT
                String nodeName = node.getName();
                SqlCommandType sqlCommandType = SqlCommandType.valueOf(nodeName.toUpperCase(Locale.ENGLISH));
                
                // 使用Builder模式创建MappedStatement对象
                // MappedStatement 包含了SQL语句的所有元信息
                MappedStatement mappedStatement = new MappedStatement.Builder(
                    configuration, msId, sqlCommandType, parameterType, resultType, sql, parameter).build();
                
                // 将MappedStatement添加到Configuration中，key是 "cn.bugstack.mybatis.dao.UserMapper.selectById"
                configuration.addMappedStatement(mappedStatement);
            }

            // ========== 注册Mapper接口 ==========
            // Resources.classForName("cn.bugstack.mybatis.dao.UserMapper") 加载Mapper接口的Class对象
            // configuration.addMapper() 会把这个Mapper接口注册到MapperRegistry中
            // 后续通过JDK动态代理创建Mapper代理对象
            configuration.addMapper(Resources.classForName(namespace));
        }
    }

}